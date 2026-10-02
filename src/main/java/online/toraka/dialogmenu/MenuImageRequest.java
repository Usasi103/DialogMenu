package online.toraka.dialogmenu;

public record MenuImageRequest(String provider, String id, int row, int column) {

    public MenuImageRequest(String provider, String id) {
        this(provider, id, 0, 0);
    }
}
