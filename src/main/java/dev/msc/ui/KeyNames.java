package dev.msc.ui;

import java.util.Locale;

import org.lwjgl.glfw.GLFW;

/** 键码 → 原版风格短名（W / LSHIFT / LCONTROL …）；不认识的就交给 GLFW 取名。 */
public final class KeyNames {
    public static String of(int key) {
        if (key >= 1000) {
            int button = key - 1000;
            return button == 3 ? "MOUSE 3" : "MOUSE " + (button + 1);
        }
        switch (key) {
            case GLFW.GLFW_KEY_W: return "W";
            case GLFW.GLFW_KEY_A: return "A";
            case GLFW.GLFW_KEY_S: return "S";
            case GLFW.GLFW_KEY_D: return "D";
            case GLFW.GLFW_KEY_Q: return "Q";
            case GLFW.GLFW_KEY_E: return "E";
            case GLFW.GLFW_KEY_F: return "F";
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCONTROL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCONTROL";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LALT";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_ESCAPE: return "ESC";
            case GLFW.GLFW_KEY_UP: return "UP";
            case GLFW.GLFW_KEY_DOWN: return "DOWN";
            case GLFW.GLFW_KEY_LEFT: return "LEFT";
            case GLFW.GLFW_KEY_RIGHT: return "RIGHT";

            default:
                String name = GLFW.glfwGetKeyName(key, 0);
                return name == null ? "KEY " + key : name.toUpperCase(Locale.ROOT);
        }
    }

    private KeyNames() {
    }
}
