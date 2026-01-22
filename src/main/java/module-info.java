module quillpad {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    opens org.openjfx.controller to javafx.fxml;
    exports org.openjfx;
    exports org.openjfx.controller;
}
