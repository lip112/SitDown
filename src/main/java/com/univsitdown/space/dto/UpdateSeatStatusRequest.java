package com.univsitdown.space.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSeatStatusRequest(@NotNull Boolean isEnabled) {}
