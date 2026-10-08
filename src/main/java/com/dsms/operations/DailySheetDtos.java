package com.dsms.operations;

import java.util.List;

public final class DailySheetDtos {
    private DailySheetDtos() {}

    public record DailySheetSummary(int active, int supported, int running, int pending, int missed, int noSupport, String totalHours) {}
    public record DailySheetRow(String candidateId, String candidateName, String groupName, String technology, String supporterId,
                                String supporterName, String engagementId, String start, String end, String duration, String dayStatus,
                                String whatsappGroupLink, String meetingUrl, String workEntryId, String notes) {}
    public record DailySheetResponse(String date, DailySheetSummary summary, List<DailySheetRow> rows) {}
    public record DailyMarkerRequest(String dayStatus, String notes) {}
}
