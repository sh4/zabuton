package io.github.sh4.zabuton.git;

import java.util.Date;
import java.util.List;

public interface ICommitObject {
    List<String> getParentCommitIds();
    User getAuthor();
    User getCommitter();
    Date getWhenSignature();
    String getMessage();
}
