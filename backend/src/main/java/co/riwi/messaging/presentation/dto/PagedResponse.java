package co.riwi.messaging.presentation.dto;

import java.util.List;

public record PagedResponse<T>(List<T> items, PageInfo pageInfo) {
}
