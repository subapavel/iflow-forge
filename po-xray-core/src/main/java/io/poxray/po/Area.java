package io.poxray.po;

import io.poxray.xi.XiRef;

/** Enterprise Services Repository or Integration Directory: URL path prefix and version context type. */
public enum Area {

    REPOSITORY("rep", XiRef.ESR),
    DIRECTORY("dir", XiRef.DIRECTORY);

    private final String path;
    private final String vcType;

    Area(String path, String vcType) {
        this.path = path;
        this.vcType = vcType;
    }

    public String path() {
        return path;
    }

    public String vcType() {
        return vcType;
    }

    public static Area of(XiRef ref) {
        return ref.isDirectory() ? DIRECTORY : REPOSITORY;
    }
}
