package com.literiskapp.api;

import java.time.LocalDate;

public class ProcessingSettings {
    public LocalDate processingStartDate;
    public LocalDate processingEndDate;
    public Timeband timeband = Timeband.Daily;
    public String reportingCurrency = "USD";
}
