package io.github.sh4.zabuton.git;

public interface ICheckoutProgress {
    long getCompletedSteps();
    long getTotalSteps();
}
