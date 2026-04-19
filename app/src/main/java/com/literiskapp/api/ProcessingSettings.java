package com.literiskapp.api;

import java.util.Date;

public class ProcessingSettings {
    private Date processingStartDate;
    private Date processingEndDate;
    private Timeband timeband = Timeband.Daily;
    private String reportingCurrency = "USD";
}
