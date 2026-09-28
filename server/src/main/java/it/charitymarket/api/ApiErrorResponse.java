package it.charitymarket.api;

public record ApiErrorResponse(
        String code,
        String message
) {
}
