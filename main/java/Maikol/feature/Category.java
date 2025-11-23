package Maikol.feature;

import java.util.List;

public class Category {
    public static String category;

    public static String combat = "Combat";
    public static String move = "Movement";
    public static String player = "Player";
    public static String render = "Render";
    public static String misc = "Misc";
    public static String client = "Client";

    public static List<String> getAll() {
        return List.of(combat, move, player, render, misc, client);
    }
}
