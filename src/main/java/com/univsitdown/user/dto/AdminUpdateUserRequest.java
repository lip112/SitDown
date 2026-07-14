package com.univsitdown.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.univsitdown.user.domain.Affiliation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AdminUpdateUserRequest {

    @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하여야 합니다.")
    private String name;

    @Pattern(
            regexp = "^\\d{3}-\\d{4}-\\d{4}$",
            message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)"
    )
    private String phone;

    private Affiliation affiliation;

    private boolean phoneProvided;
    private boolean affiliationProvided;

    public String name() {
        return name;
    }

    public String phone() {
        return phone;
    }

    public Affiliation affiliation() {
        return affiliation;
    }

    @JsonSetter
    public void setName(String name) {
        this.name = name;
    }

    @JsonSetter
    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneProvided = true;
    }

    @JsonSetter
    public void setAffiliation(Affiliation affiliation) {
        this.affiliation = affiliation;
        this.affiliationProvided = true;
    }

    @JsonIgnore
    public boolean isPhoneProvided() {
        return phoneProvided;
    }

    @JsonIgnore
    public boolean isAffiliationProvided() {
        return affiliationProvided;
    }
}
