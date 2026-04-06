package com.parking.smartparking.dto.request;

import lombok.Data;

@Data
public class ApplyVoucherRequest {

    /**
     * Voucher code to attach to a running booking. - null/blank: clear the
     * currently attached voucher
     */
    private String voucherCode;
}
