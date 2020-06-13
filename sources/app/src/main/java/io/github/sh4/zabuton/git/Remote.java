package io.github.sh4.zabuton.git;

public class Remote {
    private String name;
    private String fetchUrl;
    private String pushUrl;

    public String getName() {
        return name;
    }

    public String getFetchUrl() {
        return fetchUrl;
    }

    public String getPushUrl() {
        return pushUrl;
    }

    private Remote(String name, String fetchUrl, String pushUrl) {
        this.name = name;
        this.fetchUrl = fetchUrl;
        this.pushUrl = pushUrl;
    }
}
