package com.example.trpg.game;

public final class EndingContent {

    private EndingContent() {}

    public record Ending(String key, String title, String epilogue) {}

    // 조건 A(기관실 정지), B(서류 확보)로 결말을 정함
    // A와 B 모두: 새벽의 하차 / A만: 묻힌 진실 / A가 아니면: 내리막
    public static Ending decide(boolean engineStopped, boolean docsSecured) {
        if (engineStopped && docsSecured) {
            return new Ending("DAWN_EXIT", "새벽의 하차",
                    "열차는 터널 안에 멈춘 채 구조대를 맞이한다. 기관실이 열리고 정지 레버가 내려진 덕분에 열차는 한 걸음도 더 나아가지 못했다.\n\n"
                            + "화물칸 상자에서 나온 서류가 공개되자, 운영사가 3년 동안 제동 장치의 결함을 알면서도 숨겨 왔다는 사실이 세상에 알려진다. "
                            + "몰래 열차에 탔던 내부고발자는 증인으로 보호받는다.\n\n"
                            + "새벽의 터널 밖으로 나온 승객들은 이 밤을 오래도록 기억하게 된다.");
        }
        if (engineStopped) {
            return new Ending("BURIED_TRUTH", "묻힌 진실",
                    "열차는 출발하지 않았고, 사고는 일어나지 않았다. 구조대가 도착해 승객들은 무사히 열차에서 내린다.\n\n"
                            + "하지만 서류는 파기되었거나 누군가에게 빼앗기고 말았다. 운영사는 '일시적인 정차였다'고 발표하고, "
                            + "이 밤의 이야기를 믿어 주는 사람은 아무도 없다.\n\n"
                            + "내부고발자는 다시 도망자가 된다. 터널 안에서 있었던 일은 그 자리에 있던 사람들의 기억 속에만 남는다.");
        }
        String base = "시계가 끝나고, 열차가 터널을 빠져나가 내리막으로 들어선다. 제동 장치는 결함 그대로였다.\n\n"
                + "이 사고의 규모는 이 밤에 남긴 대비에 따라 GM이 이어서 이야기한다.\n\n";
        String extra = docsSecured
                ? "서류만은 지켜졌다. 사고 이후 조사가 시작되자 이 서류가 결함 은폐의 결정적인 증거가 된다. 너무 늦은 진실이다."
                : "서류도 함께 사라진다. 사고의 원인은 '정비 불량'이라는 한 줄로 정리되고, 진실은 사고와 함께 묻힌다.";
        return new Ending("DOWNHILL", "내리막", base + extra);
    }
}