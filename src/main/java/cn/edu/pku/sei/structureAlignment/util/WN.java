package cn.edu.pku.sei.structureAlignment.util;

import cn.edu.pku.sei.structureAlignment.tree.CodeStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.TextStructureTree;
import edu.mit.jwi.*;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.item.*;
import edu.mit.jwi.morph.WordnetStemmer;
import javafx.util.Pair;


import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by oliver on 2018/6/19.
 */
public class WN {
    public static IDictionary dict;

    static{
        String wnhome = System.getenv("WNHOME");
        System.out.println(wnhome);
        String path = wnhome + File.separator + "dict";
        URL url=null;
        try{
            url = new URL("file", null, path);
        }
        catch(MalformedURLException e){
            e.printStackTrace();
        }
        if(url != null){
            try {
                dict = new Dictionary(url);
                dict.open();
            }catch(Exception e){
                e.printStackTrace();
            }

        }
    }

    private static Map<Pair<String , String>, Double> similarity = new HashMap<>();

    public static void extend (List<CodeStructureTree> codeTrees , List<TextStructureTree> textTrees){
        Set<String> codeTokens = new HashSet<>();
        Set<String> textTokens = new HashSet<>();

        String content ;
        String camelCasePattern = "([^\\p{L}\\d]+)|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])";
        for(CodeStructureTree codeTree : codeTrees){
            for(Node node :codeTree.getAllLeafNodes()){
                if(node.isPunctuation())
                    continue;

                content = node.getContent();

                String[] tokens = content.split(camelCasePattern);
                for(int i = 0 ; i < tokens.length ; i++) {
                    codeTokens.add(tokens[i].toLowerCase());
                }
            }
        }

        for(TextStructureTree textTree : textTrees){
            for(Node node : textTree.getAllLeafNodes()){
                content = node.getContent();
                String[] tokens = content.split(camelCasePattern);
                for(int i = 0 ; i < tokens.length ; i++){
                    textTokens.add(tokens[i].toLowerCase());
                }
            }
        }

        Set<Pair<String , String>> pairs = new HashSet<>();
        String command = "python \"E:\\Intellij workspace\\StructureAlignment\\python\\test.py\" ";
        for(String codeToken : codeTokens){
            for(String textToken : textTokens){
                if(codeToken.compareTo(textToken) != 0 &&
                        !pairs.contains(new Pair<>(codeToken , textToken)) &&
                        !pairs.contains(new Pair<>(textToken , codeToken))
                        ){
                    command += codeToken + " " + textToken + " ";

                    pairs.add(new Pair<String , String>(codeToken , textToken));
                }
            }
        }

        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader( new InputStreamReader(process.getInputStream()));
            String line ;
            while((line = reader.readLine()) != null){
                if(line.trim().length() == 0) continue;
                String[] sims = line.split(" ");
                String word1 = sims[0];
                String word2 = sims[1];
                /*//乘以0.99的原因在于，通过wordnet比较的相似度，最高只能是0.99
                //因为完全相同的字符串，可以通过比较字符，判定相似度为1，
                //0.99使得，不完全相同的字符串，相似度最高只能为0.99。而只有相似度为1的节点，才能向上推导。*/
                Double sim = Double.parseDouble(sims[2]) ; // * 0.99
                if(!similarity.containsKey(new Pair<String ,String>(word1, word2 ))){
                    similarity.put(new Pair<String , String>(word1 , word2) , sim);
                    similarity.put(new Pair<String , String>(word2 , word1) , sim);
                }
            }
        }catch(Exception e ){
            e.printStackTrace();
        }


    }

    public static double getSimilarity(String word1 , String word2){
        return similarity.getOrDefault(new Pair<String , String>(word1 , word2) , 0.0);
    }

    public static void main(String[] args){
        Set<Pair<String , String>> map = new HashSet<>();
        map.add(new Pair<>("1" , "2"));
        if(map.contains(new Pair<>("2" , "1")))
            System.out.println("yes");
        else
            System.out.println("no");
    }


    static List<ISynset> getSynset(String word){
        List<ISynset> result = new ArrayList<>();
        WordnetStemmer stemmer = new WordnetStemmer(dict);
        for(POS tag: POS.values()) {
            for (String root : stemmer.findStems(word, tag)) {
                IIndexWord w = dict.getIndexWord(root, tag);
                if(w != null){
                    List<IWordID> ids = w.getWordIDs();
                    for (IWordID id : ids) {
                        ISynset set = dict.getWord(id).getSynset();
                        result.add(set);
                    }
                }
            }
        }
        return result;
    }

    public static double calculateSimilarity(String word1, String word2){
        double sim = -1;
        if(word1.trim().length() == 0 || word2.trim().length() == 0)
            return 0;

        List<ISynset> synset1 = getSynset(word1.trim());
        List<ISynset> synset2 = getSynset(word2.trim());


        for(ISynset s1: synset1){
            for(ISynset s2: synset2){
                sim = Math.max(sim, pathSimilarity(s1, s2));
            }
        }
        return sim;
    }

    static double pathSimilarity(ISynset set1, ISynset set2){
        int sim = getShortestPath(set1, set2);
        if(sim < 0) return 0;
        else return 1.0 / (sim + 1.0);

    }

    static int getShortestPath(ISynset set1, ISynset set2){
        int result = Integer.MAX_VALUE;

        if(set1.getID() == set2.getID())
            return 0;

        Map<ISynsetID, Integer> path1 = getPath(set1);
        Map<ISynsetID, Integer> path2 = getPath(set2);


        for(ISynsetID s1: path1.keySet()){
            if(path2.containsKey(s1)){
                int depth1 = path1.get(s1);
                int depth2 = path2.get(s1);
                result = Math.min(result, depth1 + depth2);
            }
        }
        return result == Integer.MAX_VALUE ? -1 : result;
    }


    static Map<ISynsetID, Integer> getPath(ISynset synset){
        Map<ISynsetID, Integer> result = new HashMap<>();
        ArrayDeque<Pair<ISynset, Integer>> deque = new ArrayDeque<>();
        deque.add(new Pair<>(synset, 0));
        while(deque.size() > 0){
            Pair<ISynset, Integer> pair =  deque.getFirst();
            deque.removeFirst();

            result.put(pair.getKey().getID(), pair.getValue());
            int depth = pair.getValue();
            for(ISynsetID id: pair.getKey().getRelatedSynsets(Pointer.HYPERNYM)){
                ISynset set = dict.getSynset(id);
                deque.push(new Pair<>(set, depth + 1));
            }

            for(ISynsetID id: pair.getKey().getRelatedSynsets(Pointer.HYPERNYM_INSTANCE)){
                ISynset set = dict.getSynset(id);
                deque.push(new Pair<>(set, depth + 1));
            }
        }

        return result;
    }
}
