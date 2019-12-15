package io.github.sh4.zabuton.git;

import java.util.Date;
import java.util.List;

public class CommitObject implements ICommitObject {
    private final List<String> parentCommitIds;
    private final User author;
    private final User committer;
    private final Date whenSignature;
    private final String message;

    public CommitObject(List<String> parentCommitIds, User author, User committer, Date whenSignature, String message) {
        this.parentCommitIds = parentCommitIds;
        this.author = author;
        this.committer = committer;
        this.whenSignature = whenSignature;
        this.message = message;
    }

    @Override
    public List<String> getParentCommitIds() {
        return parentCommitIds;
    }

    @Override
    public User getAuthor() {
        return author;
    }

    @Override
    public User getCommitter() {
        return committer;
    }

    @Override
    public Date getWhenSignature() {
        return whenSignature;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
