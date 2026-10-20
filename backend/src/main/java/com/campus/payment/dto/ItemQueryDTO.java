package com.campus.payment.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ItemQueryDTO extends PageQuery {
    private String status;
    private String category;
    private String term;
    private String keyword;
}
