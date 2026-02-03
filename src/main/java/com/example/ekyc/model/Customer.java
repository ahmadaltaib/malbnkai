package com.example.ekyc.model;

import java.util.Objects;

/**
 * Represents a customer undergoing KYC verification.
 */
public class Customer {
    private final String customerId;
    private final String fullName;
    private final String dateOfBirth; // ISO 8601 format
    private final String email;
    private final String phone;
    private final String address;
    private final String nationality;

    // Document-related fields
    private final String documentType;
    private final String documentNumber;
    private final String documentExpiryDate;
    private final String documentImageUrl;

    // Biometric-related fields
    private final String selfieUrl;
    private final String idPhotoUrl;

    // Address verification fields
    private final String proofType;
    private final String proofDate;
    private final String proofUrl;

    private Customer(Builder builder) {
        this.customerId = builder.customerId;
        this.fullName = builder.fullName;
        this.dateOfBirth = builder.dateOfBirth;
        this.email = builder.email;
        this.phone = builder.phone;
        this.address = builder.address;
        this.nationality = builder.nationality;
        this.documentType = builder.documentType;
        this.documentNumber = builder.documentNumber;
        this.documentExpiryDate = builder.documentExpiryDate;
        this.documentImageUrl = builder.documentImageUrl;
        this.selfieUrl = builder.selfieUrl;
        this.idPhotoUrl = builder.idPhotoUrl;
        this.proofType = builder.proofType;
        this.proofDate = builder.proofDate;
        this.proofUrl = builder.proofUrl;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getNationality() {
        return nationality;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getDocumentExpiryDate() {
        return documentExpiryDate;
    }

    public String getDocumentImageUrl() {
        return documentImageUrl;
    }

    public String getSelfieUrl() {
        return selfieUrl;
    }

    public String getIdPhotoUrl() {
        return idPhotoUrl;
    }

    public String getProofType() {
        return proofType;
    }

    public String getProofDate() {
        return proofDate;
    }

    public String getProofUrl() {
        return proofUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return Objects.equals(customerId, customer.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }

    @Override
    public String toString() {
        return "Customer{" +
                "customerId='" + customerId + '\'' +
                ", fullName='" + maskPii(fullName) + '\'' +
                '}';
    }

    private String maskPii(String value) {
        if (value == null || value.length() <= 2) {
            return "***";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String customerId;
        private String fullName;
        private String dateOfBirth;
        private String email;
        private String phone;
        private String address;
        private String nationality;
        private String documentType;
        private String documentNumber;
        private String documentExpiryDate;
        private String documentImageUrl;
        private String selfieUrl;
        private String idPhotoUrl;
        private String proofType;
        private String proofDate;
        private String proofUrl;

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder dateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder nationality(String nationality) {
            this.nationality = nationality;
            return this;
        }

        public Builder documentType(String documentType) {
            this.documentType = documentType;
            return this;
        }

        public Builder documentNumber(String documentNumber) {
            this.documentNumber = documentNumber;
            return this;
        }

        public Builder documentExpiryDate(String documentExpiryDate) {
            this.documentExpiryDate = documentExpiryDate;
            return this;
        }

        public Builder documentImageUrl(String documentImageUrl) {
            this.documentImageUrl = documentImageUrl;
            return this;
        }

        public Builder selfieUrl(String selfieUrl) {
            this.selfieUrl = selfieUrl;
            return this;
        }

        public Builder idPhotoUrl(String idPhotoUrl) {
            this.idPhotoUrl = idPhotoUrl;
            return this;
        }

        public Builder proofType(String proofType) {
            this.proofType = proofType;
            return this;
        }

        public Builder proofDate(String proofDate) {
            this.proofDate = proofDate;
            return this;
        }

        public Builder proofUrl(String proofUrl) {
            this.proofUrl = proofUrl;
            return this;
        }

        public Customer build() {
            return new Customer(this);
        }
    }
}
