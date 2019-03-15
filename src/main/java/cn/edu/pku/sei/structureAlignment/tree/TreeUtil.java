package cn.edu.pku.sei.structureAlignment.tree;

import cn.edu.pku.sei.structureAlignment.CodeLineRelation.CodeLineRelationGraph;
import cn.edu.pku.sei.util.Splitter;
import edu.stanford.nlp.simple.Sentence;

import java.util.ArrayList;
import java.util.List;

public class TreeUtil {
    public static List<CodeStructureTree> constructCodeTrees(String code) {
        return new CodeLineRelationGraph().build(code).getCodeLineTrees();
    }

    public static List<TextStructureTree> constructTextTrees(String description) {
        List<String> sentences = Splitter.split(description);
        return constructTextTrees(sentences);
    }

    public static List<TextStructureTree> constructTextTrees(List<String> sentences) {
        List<TextStructureTree> textTrees = new ArrayList<>();
        sentences.forEach(sentence -> {
            //System.out.println(sentence);
            textTrees.add(new TextStructureTree(0).construct(new Sentence(sentence)));
        });
        return textTrees;
    }
}
