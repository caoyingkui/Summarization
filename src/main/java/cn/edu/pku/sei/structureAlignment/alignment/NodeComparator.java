package cn.edu.pku.sei.structureAlignment.alignment;

import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import cn.edu.pku.sei.structureAlignment.util.Stemmer;
import cn.edu.pku.sei.structureAlignment.util.StopWordList;
import cn.edu.pku.sei.structureAlignment.util.WN;

import java.util.*;

/**
 * Created by oliver on 2018/7/1.
 */
public class NodeComparator {

    public static void main(String[] args){
        String s1 , s2;
        Scanner sc = new Scanner(System.in);
        while(sc.hasNext()){
            s1 = sc.nextLine();
            s2 = sc.nextLine();

            System.out.println(isAbbreviation(s1 , s2));
        }
    }

    public static double compare(Node codeNode , Node textNode){
        //if(codeNode)

        String codeContent = codeNode.getContent().trim();
        String codeLowerCase = codeContent.toLowerCase();
        String textContent = textNode.getContent().trim();
        String textLowerCase = textContent.toLowerCase();

        /*Pattern pattern = Pattern.compile("[a-zA-Z0-9]");
        java.util.regex.Matcher matcher = pattern.matcher(codeContent);
        if(!matcher.find()) // 例如content的内容为 "."
            return 0;*/

        double result = 0;

        //原始文本是否相同
        if(codeLowerCase.compareTo(textLowerCase) == 0)
            return 1.1 ;//+ (codeNode.getType() == NodeType.ADDED_METHOD_NAME ? 1 : 0);

        if(codeNode.getType() == NodeType.ADDED_KEYWORD)
            return 0.0;

        //alternatives是否相同
        if(result == 0 ){
            for(String alternative : codeNode.alternatives){
                if(alternative.toLowerCase().compareTo(textLowerCase) == 0)
                    return 1.1;
            }
        }

//        if(lcs(codeLowerCase, textLowerCase) >=5){
//            return 1.0;
//        }

        if(textNode.isStopWord())
            return 0;

        //查看驼峰是否有相似的词
        if(result == 0){
            List<String> subs = Stemmer.camelCase(codeContent);
            List<String> textSubs = Stemmer.camelCase(textContent);
            String tempString = textLowerCase;
            if(textSubs.size() > 1){
                textLowerCase = textSubs.get(textSubs.size() - 1);
            }

            for (String sub : subs) {
                if (sub.toLowerCase().compareTo(textLowerCase) == 0){
                    result = 1;
                    break;
                }

                if(isAbbreviation(sub, textLowerCase)) {
                    result = 0.99;
                    break;
                }

                double temp = WN.calculateSimilarity(sub, textLowerCase);
                if (temp > result && temp > 0.5) {
                    result = temp;
                }
            }

            textLowerCase = tempString;
        }

        //查看是否存在前缀问题
        if(result <= 0.5) {
            if(codeContent.length() >=5 && textContent.length() >= 5){
                String prefix1 = codeContent.substring(0,5).toLowerCase();
                String prefix2 = textContent.substring(0,5).toLowerCase();
                if(prefix1.compareTo(prefix2) == 0)
                    result = 0.95;
            }

        }

        //查看在additional信息中是否有相似的词。
        if(result <= 0.5){
            Set<String> codeWords = new HashSet<>();
            codeWords.addAll(Stemmer.stem(codeLowerCase + " " + codeNode.getAdditionalInfo()));

            String textBaseWord = Stemmer.stemSingleWord(textLowerCase);
            if(!StopWordList.contains(textBaseWord)) {
                for (String word : codeWords) {
                    if(StopWordList.contains(word))
                        continue;
                    if (word.compareTo(textBaseWord) == 0 ) {
                        if(textNode.isVerb() || textNode.isNoun())
                            result = 0.98;
                        else
                            result = 0.25;
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static boolean isAbbreviation(String shorter, String longer){
        shorter = shorter.toLowerCase().trim();
        longer = longer.toLowerCase().trim();

        int len1 = shorter.length();
        int len2 = longer.length();
        if(len1 >= len2 || len1 < 3 || shorter.charAt(0) != longer.charAt(0)){
            return false;
        }

        int pos = 1;
        boolean result = true;
        for(int i = 1 ; i < len1 ; i++){
            result = false;
            for(int j = pos ; j < len2 ; j ++){
                if(shorter.charAt(i) == longer.charAt(j)){
                    result = true;
                    pos = j + 1;
                    break;
                }
            }
            if(! result)
                break;
        }
        return result;
    }

    public static boolean contentEqual(Node codeNode, String word){
        String codeContent = codeNode.getContent().trim();
        String textWord = word.toLowerCase().trim();
        if(codeContent.compareTo(word) == 0)
            return true;

        for(String s : codeNode.alternatives){
            if(s.compareTo(textWord) == 0)
                return true;
        }

        return false;
    }

    // longest comment substring
    public static int lcs(String str1, String str2){
        str1 = str1.trim().toLowerCase();
        str2 = str2.trim().toLowerCase();
        int length = Math.min(str1.length(), str2.length());
        int l = length;
        while(l > 0){
            for(int i = 0 ; i + l <= length ; i ++){
                for(int j = 0  ; j + l <= length ; j++){
                    if(str1.substring(i , i + l ).compareTo(str2.substring(j, j + l)) == 0)
                        return l;
                }
            }

            l --;
        }
        return 0;
    }
}
