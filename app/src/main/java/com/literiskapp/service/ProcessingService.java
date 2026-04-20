package com.literiskapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.literiskapp.api.*;
import com.literiskapp.processing.*;
import com.literiskapp.repository.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates an asynchronous processing run. Only one job may be active
 * (PENDING or RUNNING) at any time; new submissions while busy throw
 * {@link JobBusyException}. On application startup, any orphan RUNNING/PENDING
 * jobs left behind by a previous crash are marked FAILED.
 *
 * <p>All DB work from the worker thread goes through an explicit
 * {@link TransactionTemplate} because self-invocation of {@code @Transactional}
 * methods inside the same bean bypasses Spring's AOP proxy.
 */
@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private final DealRepository dealRepo;
    private final MarketRepository marketRepo;
    private final CashflowRepository cashflowRepo;
    private final ResultRepository resultRepo;
    private final ProcessingStatusRepository statusRepo;
    private final List<CashflowGenerator> generators;
    private final ResultAggregator aggregator;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "literiskapp-processing");
        t.setDaemon(true);
        return t;
    });

    public ProcessingService(DealRepository dealRepo, MarketRepository marketRepo,
                             CashflowRepository cashflowRepo, ResultRepository resultRepo,
                             ProcessingStatusRepository statusRepo,
                             List<CashflowGenerator> generators,
                             ResultAggregator aggregator,
                             ObjectMapper mapper,
                             PlatformTransactionManager txManager) {
        this.dealRepo = dealRepo;
        this.marketRepo = marketRepo;
        this.cashflowRepo = cashflowRepo;
        this.resultRepo = resultRepo;
        this.statusRepo = statusRepo;
        this.generators = generators;
        this.aggregator = aggregator;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Mark any orphan RUNNING/PENDING jobs from a previous JVM as FAILED. */
    @PostConstruct
    public void markOrphansFailed() {
        tx.executeWithoutResult(status -> {
            List<ProcessingStatus> orphans = statusRepo.findByStatusIn(
                    List.of(ProcessingState.PENDING, ProcessingState.RUNNING));
            if (orphans.isEmpty()) return;
            LocalDateTime now = LocalDateTime.now();
            for (ProcessingStatus s : orphans) {
                s.status = ProcessingState.FAILED;
                s.finishedAt = now;
                s.errorMessage = "Interrupted by application restart";
            }
            statusRepo.saveAll(orphans);
            log.warn("Marked {} orphan processing job(s) as FAILED on startup", orphans.size());
        });
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    public ProcessingStatus submit(ProcessingSettings settings) {
        // Check-and-insert under one transaction to avoid races.
        ProcessingStatus row = tx.execute(txStatus -> {
            if (!statusRepo.findByStatusIn(
                    List.of(ProcessingState.PENDING, ProcessingState.RUNNING)).isEmpty()) {
                throw new JobBusyException();
            }
            ProcessingStatus s = new ProcessingStatus();
            s.id = UUID.randomUUID();
            s.status = ProcessingState.PENDING;
            s.requestedAt = LocalDateTime.now();
            try { s.settingsJson = mapper.writeValueAsString(settings); } catch (Exception ignore) {}
            return statusRepo.save(s);
        });

        final UUID id = row.id;
        executor.submit(() -> runJob(id, settings));
        return row;
    }

    public ProcessingStatus get(UUID id) {
        return statusRepo.findById(id).orElse(null);
    }

    public List<ProcessingStatus> list() {
        return statusRepo.findAllByOrderByRequestedAtDesc();
    }

    // ---------- Worker (runs on a background thread) ----------

    private void runJob(UUID id, ProcessingSettings settings) {
        try {
            tx.executeWithoutResult(st -> {
                ProcessingStatus s = statusRepo.findById(id).orElseThrow();
                s.status = ProcessingState.RUNNING;
                s.startedAt = LocalDateTime.now();
                statusRepo.save(s);
            });

            long[] counts = tx.execute(st -> doRun(settings));

            tx.executeWithoutResult(st -> {
                ProcessingStatus s = statusRepo.findById(id).orElseThrow();
                s.status = ProcessingState.FINISHED;
                s.finishedAt = LocalDateTime.now();
                s.cashflowsGenerated = counts[0];
                s.resultsGenerated = counts[1];
                statusRepo.save(s);
            });
        } catch (Exception ex) {
            log.error("Processing job {} failed", id, ex);
            try {
                tx.executeWithoutResult(st -> {
                    statusRepo.findById(id).ifPresent(s -> {
                        s.status = ProcessingState.FAILED;
                        s.finishedAt = LocalDateTime.now();
                        String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                        s.errorMessage = msg.length() > 4000 ? msg.substring(0, 4000) : msg;
                        statusRepo.save(s);
                    });
                });
            } catch (Exception inner) {
                log.error("Failed to persist FAILED status for job {}", id, inner);
            }
        }
    }

    /** Returns {cashflows, results} counts. */
    private long[] doRun(ProcessingSettings settings) {
        cashflowRepo.truncate();
        resultRepo.truncate();

        List<Deal> deals = dealRepo.findAll();
        List<Market> markets = marketRepo.findAll();
        MarketDataService md = new MarketDataService(markets);

        Map<String, CashflowGenerator> byType = new HashMap<>();
        for (CashflowGenerator g : generators) byType.put(g.supports().toUpperCase(Locale.ROOT), g);

        List<Cashflow> allCashflows = new ArrayList<>();
        for (Deal d : deals) {
            if (d.type == null) continue;
            CashflowGenerator gen = byType.get(d.type.toUpperCase(Locale.ROOT));
            if (gen == null) {
                log.warn("No generator for deal type '{}'; skipping deal {}", d.type, d.id);
                continue;
            }
            allCashflows.addAll(gen.generate(d, md, settings));
        }
        cashflowRepo.saveAll(allCashflows);

        List<Result> results = aggregator.aggregate(deals, allCashflows, md, settings);
        resultRepo.saveAll(results);

        return new long[]{ allCashflows.size(), results.size() };
    }

    public static class JobBusyException extends RuntimeException {
        public JobBusyException() { super("Another processing job is already active"); }
    }
}
