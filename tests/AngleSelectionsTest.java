import io.github.yixing233.hyperduo.ui.AngleSelections;

public class AngleSelectionsTest {
    public static void main(String[] args) {
        AngleSelections x = AngleSelections.parse("com.example.one,com.example.two", "native,angle");
        x.setAngle("com.example.three", true);
        if (!x.packages().equals("com.example.one,com.example.two,com.example.three") || !x.values().equals("native,angle,angle")) throw new AssertionError("preserve choices");
        x.setAngle("com.example.two", false);
        if (!x.packages().equals("com.example.one,com.example.three") || !x.values().equals("native,angle")) throw new AssertionError("remove only target");
        x.setAngle("com.example.one", false);
        if (!x.values().equals("native,angle")) throw new AssertionError("preserve non-ANGLE choice");
        AngleSelections empty = AngleSelections.parse(null, null);
        empty.setAngle("com.example.three", true);
        empty.setAngle("com.example.three", false);
        if (!empty.packages().isEmpty() || !empty.values().isEmpty()) throw new AssertionError("empty selection round trip");
        boolean rejected = false;
        try { AngleSelections.parse("one,two", "angle"); } catch (IllegalArgumentException e) { rejected = true; }
        if (!rejected) throw new AssertionError("reject mismatched settings");
        System.out.println("PASS: ANGLE selection preservation");
    }
}
