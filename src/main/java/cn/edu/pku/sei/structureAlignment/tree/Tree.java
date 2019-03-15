package cn.edu.pku.sei.structureAlignment.tree;

import cn.edu.pku.sei.structureAlignment.Printer;
import cn.edu.pku.sei.structureAlignment.parser.nlp.Dependency;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import javafx.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Created by oliver on 2017/12/23.
 */
public abstract class Tree<T extends Tree<T>>{
    public Node root;
    protected List<T> children;
    protected Tree parent;

    protected int width;
    protected int rootWidth;// when display the tree , the width of the the root

    protected int startIndex;   // record the starting coded number of a tree
    protected int endIndex;     // record the ending coded number of a tree

    //region <setter>

    //对于文本树来说，就是在进行自然语言解析的时候，产生的依赖关系信息
    //对于代码树来说，特指是MethodInvocation这个节点，该类节点往往能够找到相关的javadoc信息，这样一来，就可以解析其中的依赖信息
    public List<Dependency> dependencies;


    public void setChildren(List<T> children) {
        this.children = children;
    }

    public void setParent(Tree parent){
        this.parent = parent;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setRootWidth(int rootWidth) {
        this.rootWidth = rootWidth;
    }


    //endregion <setter>

    //region <getter>

    public int getId(){
        return root.getId();
    }

    public Tree getParent(){
        return this.parent;
    }

    public List<T> getChildren() {
        return children;
    }

    public int getWidth() {
        return width;
    }

    public int getRootWidth() {
        return rootWidth;
    }

    public String getContent(){
        return root.getContent();
    }

    public int getStartIndex(){
        return startIndex;
    }

    public int getEndIndex(){
        return endIndex;
    }

    public int getChildrenSize(){
        return children.size();
    }
    //endregion <getter>

    public abstract String getDisplayContent();

    public int calculateWidth(int margin , Graphics g){
        int result = 0 ;
        margin = ( margin < 1 ? 1 : margin);

        String displayContent = getDisplayContent();

        if(children.size() == 0){
            displayContent = getDisplayContent();
            result = width = rootWidth = g.getFontMetrics().stringWidth(displayContent) + margin;
        }else{
            result += ( margin * children.size());

            for(Tree child : children){
                result += child.calculateWidth(margin , g);
            }

            displayContent = getDisplayContent();
            rootWidth = g.getFontMetrics().stringWidth(displayContent);

            width = result = ( result > rootWidth ? result : rootWidth);
        }
        return result;
    }


    /**
     *
     * @param x tree显示区域上边框的中间位置x坐标
     * @param y tree显示区域上边框的中间位置y坐标
     * @param textHeight 显示的文字的高度
     * @param lineHeight 每行显示的文字，中间相间的间隔
     * @param margin  每个子部分中间的间隔
     * @param g
     */
    public void print(int x , int y , int textHeight , int lineHeight , int margin , Graphics g){
        int text_x = x - rootWidth / 2;
        int text_y = y + textHeight ;

        g.drawString(getDisplayContent() , text_x , text_y);
        //g.drawRect(text_x - 3, text_y - 12 , rootWidth + 4 , 14);



        if(children.size() == 1){
            children.get(0).print(x , y + textHeight + lineHeight , textHeight , lineHeight , margin , g);
            g.drawLine(x, y + textHeight +  3, x, y + textHeight + lineHeight - 3);
        }
        else if(children.size() > 1){

            List<Tree> remainTrees = new ArrayList<Tree>();

            int child_x = x - width / 2 + margin / 2;
            int child_y = y + lineHeight + textHeight;
            int start = 0 , end = 0 , i;

            Tree child ;

            int childrenTotalLength = 0;

            for(i = 0 ; i < children.size() ; i ++ ){
                child = children.get(i);

                if(child.children.size() == 0 && i != 0 && i != children.size() - 1){ //第一棵树和最后一棵不算
                    child_x += child.getWidth() / 2;
                    remainTrees.add(child);

                    child_x += ( margin +  child.getWidth() / 2 ) ;

                    childrenTotalLength += child.getWidth();
                }else{
                    if(remainTrees.size() != 0 ){
                        end = child_x + margin + child.width / 2 - child.rootWidth / 2;
                        int remainSize = remainTrees.size();

                        int step = (end - start - childrenTotalLength )/ (remainSize + 1);
                        start += step;
                        for(int j = 0 ; j < remainSize ; j++){
                            Tree remainTree = remainTrees.get(j);

                            //画出连接线
                            g.drawLine(x , y + textHeight + 3 , start + remainTree.getWidth() / 2  , child_y - 3);

                            remainTree.print(start + remainTree.getWidth() / 2 , child_y , textHeight , lineHeight , margin , g);
                            start += (remainTree.getWidth() + step) ;
                        }

                        childrenTotalLength = 0;
                        remainTrees = new ArrayList<Tree>();
                    }
                    child_x += child.getWidth() / 2;

                    g.drawLine(x , y + textHeight + 3 , child_x , child_y - 3);
                    child.print(child_x , child_y , textHeight , lineHeight , margin , g);

                    start = child_x  + child.rootWidth / 2;
                    child_x += ( margin +  child.getWidth() / 2 ) ;
                }

            }
        }
    }

    public void print(){
        JFrame frame = new JFrame();
        Printer printer = new Printer(this);
        printer.setBackground(Color.white);
        frame.add(printer);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 1200);
        frame.setVisible(true);
    }


    public T compress(){
        if(children.size() == 0) return (T)this;
        else if(children.size() == 1) return children.get(0).compress();
        else{
            List<T> temp = new ArrayList<T>();
            for(T child: children){
                temp.add(child.compress());
            }

            children.clear();
            setChildren(temp);
            return (T)this;
        }

    }

    public Map<Pair<Integer , Integer> , Double> compare(Tree tree){
        Map<Pair<Integer , Integer> , Double> result = new HashMap<Pair<Integer, Integer>, Double>();

        List<Node> nodes1 = getAllNodes();
        List<Node> nodes2 = tree.getAllNodes();

        for(Node node1 : nodes1){
            for(Node node2 : nodes2){
                double similarity = Node.compare(node1, node2, null, null);
                result.put(new Pair<Integer , Integer>(node1.getId() , node2.getId()) , similarity);
            }
        }
        return result;
    }


    public T getTree(int id){
        if(root.id == id) return (T) this;
        else {
            for(T child : children){
                if(child.startIndex <= id && child.endIndex >= id){
                    return child.getTree(id);
                }
            }
        }
        return null;
    }

    public Node getNode(int id){
        T tree = getTree(id);
        if(tree != null)
            return tree.root;
        else
            return null;
    }

    public List<Node> getAllNodes(){
        List<Node> result = new ArrayList<Node>();
        result.add(root);

        for(T child : children){
            result.addAll(child.getAllNodes());
        }
        return result;
    }

    public List<T> getAllTrees(){
        List<T> result = new ArrayList<>();
        result.add((T)this);

        for(T child : children){
            result.addAll(child.getAllTrees());
        }

        return result;
    }

    public List< Node> getAllLeafNodes(){
        List<Node> result = new ArrayList<>();

        if(children.size() == 0){
            result.add(root);
        } else{
            for(T child : children){
                result.addAll(child.getAllLeafNodes());
            }
        }

        Collections.sort(result , new Comparator<Node>(){
            @Override
            public int compare(Node o1, Node o2) {
                return o1.id - o2.id;
            }
        });
        return result;
    }

    public List<T> getAllLeafTrees(){
        List<T> result = new ArrayList<>();

        if(children.size() == 0){
            result.add((T)this);
        } else{
            for(T child : children){
                result.addAll(child.getAllLeafTrees());
            }
        }

        Collections.sort(result, new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                return o1.root.id - o2.root.id;
            }
        });
        return result;
    }

    public List<Node> getAllNonleafNodes() {
        List<Node> result =  new ArrayList<>();
        if(children.size() > 0){
            result.add(root);
            for(T child : children){
                result.addAll(child.getAllNonleafNodes());
            }
        }
        return result;
    }

    public List<T> getAllNonleafTrees(){
        List<T> result = new ArrayList<>();
        if(children.size() > 0){
            result.add((T)this);
            for(T child : children){
                result.addAll(child.getAllNonleafTrees());
            }
        }
        return result;
    }


    /**
     * get two leaf nodes' common parent
     * id1: the first node's id , id2: the second node's id
     */
    public int findCommonParents(int id1 , int id2 , int maxDepth){
        List<Integer> parentPath1 = findPathToNode(id1);
        List<Integer> parentPath2 = findPathToNode(id2);

        if(parentPath1 == null || parentPath2 == null) return -1;

        int i , j , bound1 ,bound2;


        if(maxDepth > 0) {
            bound1 = parentPath1.size() - 1 - maxDepth > 0 ? parentPath1.size() - 1 - maxDepth : 0;
            bound2 = parentPath2.size() - 1 - maxDepth > 0 ? parentPath2.size() - 1 - maxDepth : 0;
        }else if (maxDepth == 0){
            bound1 = bound2 = 0;
        }else{
            bound1 = parentPath1.size();
            bound2 = parentPath2.size();
        }

        for(i = parentPath1.size() - 2 ; i >= bound1 ; i --){
            j = parentPath2.size() - 2;

            for( ; j >= bound2 ; j--){
                if(parentPath1.get(i) == parentPath2.get(j) ) return parentPath1.get(i);
            }
        }

        return -1;
    }

    public int findCommonParents(Set<Integer> nodes){

        if(nodes.size() == 1)
        {
            return ((Integer)(nodes.toArray()[0]));
        }

        List< List<Integer>> paths = new ArrayList<List<Integer>>();

        for(int node : nodes){
            List<Integer> path = findPathToNode(node);
            if(path == null)
                return -1;

            paths.add( path );
        }


        int result = -1;
        if(paths.size() > 1) {
            for (int i = 0; ; i++) {
                int r = -1;
                if (paths.get(0).size() > i) {
                    r = paths.get(0).get(i);
                }

                for( int j = 1 ; j < paths.size()  ; j ++){
                    List<Integer> path = paths.get(j);
                    if(path.size() <= i || path.get(i) != r){
                        return result;
                    }
                }
                result = r;
            }
        }

        return result;//估计不会执行到这句话。
    }


    /**
     * findCommonParents are used to find the common parent of a group of nodes, maxDepth is the max depth from the parent to the lowest child.
     * @param nodes
     * @param maxDepth
     * @return
     */
    public Map<Integer , List<Integer>> findCommonParents(List<Integer> nodes , int maxDepth){
        Map<Integer , List<Integer> >result = new HashMap<Integer, List<Integer>>();
        Map<Integer , List<Integer>> paths = new HashMap<Integer, List<Integer>>();


        Map<Integer , List<List<Integer>>> groups =  new HashMap<Integer , List<List<Integer>>>();
        for(int node : nodes){
            List<Integer> path = findPathToNode(node);

            if(path != null){
                int r = path.get(0);
                if(groups.containsKey(r)){
                    groups.get(r).add(path);
                }else{
                    List<List<Integer>> temp = new ArrayList<List<Integer>>();
                    temp.add(path);
                    groups.put(r , temp);
                }
            }

            paths.put(node , path);
        }


        for(int group : groups.keySet()){
            result.putAll(  findCommonParents(maxDepth , groups.get(group))  );
        }

        return result;
    }


    //paths中记录的一定数量的从某个相同的顶点到不同节点的路径， 并且这些路径的最后一个节点就是要到达的顶点。
    //所以可以认为，每一条路径至少要有两个元素:第一个共同的顶点 + 其他路径结点 + 最后到达的节点。
    //在第一次调用该函数时，可以认为从root节点到不同节点的路径，因此paths中的所有元素的第一个元素都为0（root节点）
    //在接下来递归调用该函数时，需要找到有相同的父亲节点的路径。
    private Map<Integer , List<Integer>> findCommonParents(int maxDepth , List<List<Integer>> paths ){
        Map<Integer , List<Integer>> result = new HashMap<Integer, List<Integer>>();

        //如果某个路径只有一个结点的话，丢弃它，这种情况可能会在逐步递归时出现。
        Iterator<List<Integer>> it = paths.iterator();
        while(it.hasNext()){
            if(it.next().size() < 2){
                it.remove();
            }
        }


        //例如 paths 为 {{0 1  4  5 } , {0 1 7 8} , {0 2 3  9} , {0 2 10 11}}
        //可以看出 从0 可以 到达四个点 ： 5 ， 8 ， 9 ， 11
        //groups 用于，在从上往下找共同父节点时，出现的分歧现象：
        //例如，上述节点在去掉首个共同节点0后，就出现两种情况，以1为父节点的一组，以2为父节点的一组，因此，用groups去记录这些分组。
        Map<Integer , List<List<Integer>>> groups = new HashMap<Integer, List<List<Integer>>>() ;

        //因为要找公共父节点嘛， 所以至少得有两条路径呀
        if(paths.size() > 1) {
            //当出现分歧时，sameRoot，就可以作为一个公共父节点返回。
            int sameRoot = paths.get(0).get(0);

            //number是为了去看所有路径的第二个点是否出现分歧现象，因为第一个点，肯定都一样。
            //因此需要去判断是否所有的第二个节点都一样。allSame用于标记是否一样。
            int number = paths.get(0).get(1);
            boolean allSame = true;

            for (List<Integer> path : paths) {
                if(path.get(1) != number){
                    allSame = false;
                }

                //把一个公共的父节点都去掉，
                path.remove(0);
            }

            //如果出现分歧，那么这个时候sameRoot就可以作为一个结果去返回，但是要求一定在maxDepth的要求下
            //下面的if语句，就是当出现分歧时，如果从sameRoot到某些节点的depth(也就是剩下path的长度)小于等于maxDepth，
            //那么sameRoot可以作为这些节点的公共父节点返回。

            if(!allSame){

                //childNodes用于记录sameRoot可以作为那些节点的公共父节点
                List<Integer> childNodes = new ArrayList<Integer>();

                for(List<Integer> path : paths){
                    //从sameRoot到该节点的深度小于等于maxDepth，就记录一下，取path的最后一个元素。
                    if(path.size() <= maxDepth){
                        childNodes.add(path.get(path.size() - 1));
                    }
                }

                //公共父节点至少得两个吧
                if(childNodes.size() > 1){
                    result.put(sameRoot , childNodes);
                }
            }

            if(allSame){
                //如果没有出现分歧现象，那就把去除第一个节点的路径，继续递归求解。
                result.putAll(  findCommonParents(maxDepth , paths) );
            }else {
                //既然出现了分歧现象，那么就需要去分组，分组是以第二个（因为已经把第一个给去掉，所以是当前的第一个）为分组依据
                for (List<Integer> path : paths) {
                    int r = path.get(0);
                    if(groups.containsKey(r)){
                        groups.get(r).add(path);
                    }else{
                        List<List<Integer>> ps = new ArrayList<List<Integer>>();
                        ps.add(path);
                        groups.put(r , ps);
                    }
                }

                for(int r : groups.keySet()){
                    List<List<Integer>> group = groups.get(r);
                    //然后针对组别中含有两个以上的节点组别继续求解
                    if(group.size() > 1) result.putAll(findCommonParents(maxDepth , group));
                 }

            }
        }

        return result;
    }

    /**
     * @param id
     * @return the path from the root to the node, if the path exist,
     *          null, if no.
     */
    private List<Integer> findPathToNode(int id){
        List<Integer> result = new ArrayList<Integer>();

        Tree<T> temp = this;
        int start , end , childLength , i;
        while(temp != null){
            if(temp.getId() == id){
                result.add(temp.getId());
                break;
            }

            childLength = temp.children.size();
            end = id + 1;
            //在建立树的时候，严格按照深度优先的编号，因此，一个树的所有子节点最小结点就是root的编号，而最大的结点就是他的sibling的root编号减1
            for(i = childLength - 1 ; i > -1 ; i -- ){
                start = temp.children.get(i).getId();
                if(id >= start && id < end){
                    result.add(temp.getId());
                    temp = temp.children.get(i);
                    break;
                }else if (id > end){
                    return null;
                }else{
                    end = start ;
                }
            }
            if(i == -1 ){
                return null;
            }
        }

        if(temp == null){
            return null;
        }

        return result;
    }

    public void clean() {
        this.getAllLeafNodes().stream().forEach(node -> node.matchedCodeNodeList = new ArrayList<>());
    }
}
