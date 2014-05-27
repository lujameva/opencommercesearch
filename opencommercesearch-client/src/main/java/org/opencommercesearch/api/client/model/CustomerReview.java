package org.opencommercesearch.api.client.model;

public class CustomerReview {

    private int count;
    private float average;
    private float bayesianAverage;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public float getAverage() {
        return average;
    }

    public void setAverage(float average) {
        this.average = average;
    }

    public float getBayesianAverage() {
        return bayesianAverage;
    }

    public void setBayesianAverage(float bayesianAverage) {
        this.bayesianAverage = bayesianAverage;
    }
    
}
