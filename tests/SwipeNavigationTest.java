package io.github.yixing233.hyperduo.ui;
public class SwipeNavigationTest {
    private static void check(int expected,int current,float dx,float width,float density) {
        int got=SwipeNavigation.targetIndex(current,3,dx,width,density);
        if(got!=expected)throw new AssertionError("expected "+expected+", got "+got);
    }
    public static void main(String[] args) {
        check(1,0,-300,1000,3);check(0,1,300,1000,3);check(1,1,150,1000,3);
        check(0,0,300,1000,3);check(2,2,-300,1000,3);check(2,1,-300,1000,3);
        check(1,1,Float.NaN,1000,3);check(1,1,300,0,3);
        System.out.println("PASS: swipe direction, threshold, boundaries and invalid input");
    }
}
