package com.roommatch.platform;

/** The platform's own search parameters. Dates are ISO strings, as in a REST query. All optional. */
public class PlatformSearchFilter {
    private final String city;
    private final Double rentMin;
    private final Double rentMax;
    private final String availableFrom;
    private final String availableTo;

    public PlatformSearchFilter(String city, Double rentMin, Double rentMax,
                                String availableFrom, String availableTo) {
        this.city = city;
        this.rentMin = rentMin;
        this.rentMax = rentMax;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
    }

    public String getCity() { return city; }
    public Double getRentMin() { return rentMin; }
    public Double getRentMax() { return rentMax; }
    public String getAvailableFrom() { return availableFrom; }
    public String getAvailableTo() { return availableTo; }
}
