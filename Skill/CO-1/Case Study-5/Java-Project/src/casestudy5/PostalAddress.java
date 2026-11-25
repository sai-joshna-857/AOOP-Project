package casestudy5;

public record PostalAddress(String street, String city, String postalCode, String country) {

    public PostalAddress {
        street = requireText(street, "street");
        city = requireText(city, "city");
        postalCode = requireText(postalCode, "postal code");
        country = requireText(country, "country");

        if (country.equalsIgnoreCase("India") || country.equalsIgnoreCase("IN")) {
            if (!postalCode.matches("[1-9]\\d{5}")) {
                throw new DomainException(
                    "Invalid Indian PIN code (needs 6 digits, first 1-9): '" + postalCode + "'");
            }
        } else if (!postalCode.matches("[A-Za-z0-9][A-Za-z0-9 \\-]{2,9}")) {
            throw new DomainException("Invalid postal code: '" + postalCode + "'");
        }
    }

    public static PostalAddress of(String street, String city, String postalCode, String country) {
        return new PostalAddress(street, city, postalCode, country);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainException("Address " + field + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return street + ", " + city + " - " + postalCode + ", " + country;
    }
}