package it.charitymarket.sale;

public record SaleLineResponse (
        String itemId,
        String itemCode,
        String itemName,
        long finalPriceCents
){
}
