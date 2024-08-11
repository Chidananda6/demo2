package com.task10.dto;


import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
    private int tableNumber;
    private String clientName;
    private String phoneNumber;
    private String date; // Should be in yyyy-MM-dd format
    private String slotTimeStart; // Should be in HH:MM format
    private String slotTimeEnd;
}