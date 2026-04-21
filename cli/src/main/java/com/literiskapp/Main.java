package com.literiskapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.nio.file.Path;

public class Main {

    private static final String USAGE = """
            Usage: java -Dconfig=<config.yml> -jar cli.jar <entity> <command> [args]

            API entities (require -Dconfig):
              deals      list                    List all deals
              deals      insert  file=<path>     Insert deals from JSON array file
              deals      truncate                Delete all deals
              markets    list                    List all market data
              markets    insert  file=<path>     Insert market data from JSON array file
              markets    truncate                Delete all market data
              cashflows  list                    List all cashflows
              cashflows  truncate                Delete all cashflows
              results    list                    List all results
              results    truncate                Delete all results
              process    start   file=<path>     Start async processing with JSON settings file
              process    status  id=<uuid>       Fetch one job's status
              process    list                    List all processing jobs (newest first)

            File conversion (no -Dconfig needed):
              convert    jsonToCsv  file=<path>  Convert JSON array to CSV
              convert    csvToJson  file=<path>  Convert CSV to JSON array (types inferred)
              convert    jsonToXLS  file=<path>  Convert JSON array to XLSX (types set)
              convert    XLSToJson  file=<path>  Convert XLSX to JSON array

            Config file (YAML):
              baseUrl: http://localhost:8082
              bearerToken: your-secret-token

            Processing settings JSON example:
              {
                "processingStartDate": "2024-01-01",
                "processingEndDate":   "2025-12-31",
                "timeband": "Monthly",
                "reportingCurrency": "USD"
              }
            """;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(USAGE);
            return;
        }

        String entity  = args[0].toLowerCase();
        String command = args.length > 1 ? args[1].toLowerCase() : "";

        // ── File conversion commands: no server config needed ──────────────
        if ("convert".equals(entity)) {
            handleConvert(command, args);
            return;
        }

        // ── All other commands: config + server required ───────────────────
        String configPath = System.getProperty("config");
        if (configPath == null) {
            System.err.println("Error: -Dconfig=<path> is required.");
            System.err.println(USAGE);
            System.exit(1);
        }

        Config config = new ObjectMapper(new YAMLFactory())
                .readValue(new File(configPath), Config.class);

        ApiClient client = new ApiClient(config);

        switch (entity) {
            case "deals" -> {
                switch (command) {
                    case "list"     -> client.get("/api/deals");
                    case "truncate" -> client.delete("/api/deals");
                    case "insert"   -> client.post("/api/deals", requireFile(args));
                    default         -> entityUsage("deals", true);
                }
            }
            case "markets" -> {
                switch (command) {
                    case "list"     -> client.get("/api/markets");
                    case "truncate" -> client.delete("/api/markets");
                    case "insert"   -> client.post("/api/markets", requireFile(args));
                    default         -> entityUsage("markets", true);
                }
            }
            case "cashflows" -> {
                switch (command) {
                    case "list"     -> client.get("/api/cashflows");
                    case "truncate" -> client.delete("/api/cashflows");
                    default         -> entityUsage("cashflows", false);
                }
            }
            case "results" -> {
                switch (command) {
                    case "list"     -> client.get("/api/results");
                    case "truncate" -> client.delete("/api/results");
                    default         -> entityUsage("results", false);
                }
            }
            case "process" -> {
                switch (command) {
                    case "start"  -> client.postSingle("/api/process", requireFile(args));
                    case "status" -> {
                        String id = requireArg(args, "id");
                        client.get("/api/process/" + id);
                    }
                    case "list"   -> client.get("/api/process");
                    default       -> processUsage();
                }
            }
            default -> {
                System.err.println("Unknown entity: " + args[0]);
                System.out.println(USAGE);
                System.exit(1);
            }
        }
    }

    // ── Convert dispatcher ─────────────────────────────────────────────────

    private static void handleConvert(String command, String[] args) throws Exception {
        switch (command) {
            case "jsontocsv" -> FileConverter.jsonToCsv(requireFile(args));
            case "csvtojson" -> FileConverter.csvToJson(requireFile(args));
            case "jsontoxls" -> FileConverter.jsonToXls(requireFile(args));
            case "xlstojson" -> FileConverter.xlsToJson(requireFile(args));
            default          -> convertUsage();
        }
    }

    // ── Argument helpers ───────────────────────────────────────────────────

    private static Path requireFile(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("file=")) return Path.of(arg.substring(5));
        }
        System.err.println("Error: file=<path> argument is required.");
        System.exit(1);
        return null;
    }

    private static String requireArg(String[] args, String name) {
        String prefix = name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) return arg.substring(prefix.length());
        }
        System.err.println("Error: " + name + "=<value> argument is required.");
        System.exit(1);
        return null;
    }

    // ── Usage printers ─────────────────────────────────────────────────────

    private static void entityUsage(String entity, boolean hasInsert) {
        System.out.println("Commands for '" + entity + "':");
        System.out.println("  list                  List all " + entity);
        if (hasInsert) {
            System.out.println("  insert  file=<path>   Insert " + entity + " from JSON array file");
        }
        System.out.println("  truncate              Delete all " + entity);
        System.exit(1);
    }

    private static void processUsage() {
        System.out.println("Commands for 'process':");
        System.out.println("  start   file=<path>   Submit a new processing job (JSON settings file)");
        System.out.println("  status  id=<uuid>     Fetch one job's status");
        System.out.println("  list                  List all jobs");
        System.exit(1);
    }

    private static void convertUsage() {
        System.out.println("Commands for 'convert' (no -Dconfig needed):");
        System.out.println("  jsonToCsv  file=<path>   Convert JSON array to CSV");
        System.out.println("  csvToJson  file=<path>   Convert CSV to JSON array (types inferred)");
        System.out.println("  jsonToXLS  file=<path>   Convert JSON array to XLSX (typed cells)");
        System.out.println("  XLSToJson  file=<path>   Convert XLSX to JSON array");
        System.exit(1);
    }
}
