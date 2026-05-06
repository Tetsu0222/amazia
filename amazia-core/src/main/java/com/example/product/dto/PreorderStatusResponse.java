package com.example.product.dto;

import com.example.product.entity.PreorderStatus;
import com.example.product.entity.Product;

import java.time.LocalDate;

public class PreorderStatusResponse {

    private Long productId;
    private PreorderStatus status;
    private LocalDate releaseDate;
    private LocalDate preorderStartDate;
    private boolean acceptPreorder;
    private boolean acceptBackorder;

    public PreorderStatusResponse() {}

    public static PreorderStatusResponse of(Product product, PreorderStatus status) {
        PreorderStatusResponse r = new PreorderStatusResponse();
        r.productId = product.getId();
        r.status = status;
        r.releaseDate = product.getReleaseDate();
        r.preorderStartDate = product.getPreorderStartDate();
        r.acceptPreorder = product.isAcceptPreorder();
        r.acceptBackorder = product.isAcceptBackorder();
        return r;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public PreorderStatus getStatus() { return status; }
    public void setStatus(PreorderStatus status) { this.status = status; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public LocalDate getPreorderStartDate() { return preorderStartDate; }
    public void setPreorderStartDate(LocalDate preorderStartDate) { this.preorderStartDate = preorderStartDate; }
    public boolean isAcceptPreorder() { return acceptPreorder; }
    public void setAcceptPreorder(boolean acceptPreorder) { this.acceptPreorder = acceptPreorder; }
    public boolean isAcceptBackorder() { return acceptBackorder; }
    public void setAcceptBackorder(boolean acceptBackorder) { this.acceptBackorder = acceptBackorder; }
}
