package io.github.sh4.zabuton.git;

public class ResetProgress implements ICheckoutProgress {
    private long totalSteps;
    private long completedSteps;

    @Override
    public long getCompletedSteps() {
        return completedSteps;
    }

    @Override
    public long getTotalSteps() {
        return totalSteps;
    }
}
