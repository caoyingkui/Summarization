package cn.edu.pku.sei.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Splitter {
    public static List<String> split(String paragraph) {
        List<String> result = new ArrayList<>();
        return Arrays.stream(paragraph.split("\\n")).
                flatMap(para -> Arrays.stream(para.split("\\. "))).
                filter(sentence -> !sentence.trim().equals("")).
                collect(Collectors.toList());
    }

}
