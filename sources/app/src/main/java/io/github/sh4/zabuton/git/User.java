package io.github.sh4.zabuton.git;

import java.util.Date;

public class User {
    private final String name;
    private final String email;
    private final Date whenSignature;

    public User(String name, String email, Date whenSignature) {
        this.name = name;
        this.email = email;
        this.whenSignature = whenSignature;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Date getWhenSignature() { return whenSignature; }
}
