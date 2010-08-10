/**
 * Copyright (C) 2009 Progress Software, Inc.
 * http://fusesource.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.mvnplugins.graph;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class DependencyVisualizer {

    LinkedHashMap<String, Node> nodes = new LinkedHashMap<String, Node>();
    LinkedHashSet<Edge> edges = new LinkedHashSet<Edge>();
    HashSet<String> hideScopes = new HashSet<String>();
    boolean hideOptional;
    boolean hidePoms;
    boolean hideOmitted;
    boolean hideExternal;
    boolean hideVersion;
    boolean hideGroupId;
    boolean hideType;
    boolean keepDot;
    String label;
    boolean hideTransitive;
    Log log;
    boolean cascade;
    String direction="TB";
    URLCreator urlCreator;

    private class Node {
        private final String id;
        private final ArrayList<Edge> children = new ArrayList<Edge>();
        private final ArrayList<Edge> parents = new ArrayList<Edge>();
        private final Artifact artifact;
        private int roots;

        public Node(String id, Artifact artifact) {
            this.id = id;
            this.artifact = artifact;
        }

        @Override
        public boolean equals(Object obj) {
            return id.equals(((Node) obj).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id;
        }

        public boolean isHidden() {
            if ( hidePoms && isExclusivelyType("pom") ) {
                return true;
            }
            if( hideExternal && roots == 0 ) {
                return true;
            }
            return false;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            final Artifact a = artifact;
            StringBuilder sb = new StringBuilder();
            if( !hideGroupId ) {
                sb.append( a.getGroupId() + "\\n");
            }
            sb.append( a.getArtifactId());
            if( !hideType ) {
                if (!isExclusivelyType("jar")) {
                    sb.append("\\n");
                    boolean first=true;
                    for (String type : getTypes()) {
                        if( !first  ) {
                            sb.append(" | ");
                        }
                        first=false;
                        sb.append(type);
                    }
                }
            }
            if( !hideVersion ) {
                sb.append("\\n" + a.getVersion());
            }
            return sb.toString();
        }

        public String getColor() {
            if (isScope("test")) {
                return "cornflowerblue";
            }
            if (isOptional()) {
                return "green";
            }
            if (isScope("provided")) {
//                return "darkgrey";
                return "#a9a9a9"; // color name unknown 
            }
            return "black";
        }
        
        public String getURL() {
            if(urlCreator == null) {
                return null;
            }
            
            return urlCreator.getURL(artifact);
        }

        private boolean isScope(String scope) {
            return roots==0 && !parents.isEmpty() && allMatchScope(parents, scope);
        }

        public String getFillColor() {
            if( roots > 0 ) {
                return "#dddddd"; 
            }
            return "white";
        }
        public String getFontColor() {
            return getColor();
        }

        public String getLineStyle() {
            String rc = isOptional() ? "dotted" : "solid";
            rc += ",filled";
            return rc;
        }

        public double getFontSize() {
            if( roots > 0 ) {
                return 14;
            }
            return 8;
        }

        public boolean isOptional() {
            return roots==0 && !parents.isEmpty() && allMatchOptional(parents, true);
        }


        private boolean allMatchScope(ArrayList<Edge> edges, String scope) {
            for (Edge e : edges) {
                if (!e.isScope(scope)) {
                    return false;
                }
            }
            return true;
        }
        private boolean allMatchOptional(ArrayList<Edge> edges, boolean value) {
            for (Edge e : edges) {
                if (e.optional != value) {
                    return false;
                }
            }
            return true;
        }

        private Set<String> getTypes() {
            LinkedHashSet<String> rc = new LinkedHashSet<String>();
            rc.add(artifact.getType() + (artifact.getClassifier()==null? "" : (":" + artifact.getClassifier())));
            for (Edge e : parents) {
                Artifact artifact = e.dependencyNode.getArtifact();
                rc.add(artifact.getType() + (artifact.getClassifier()==null? "" : (":" + artifact.getClassifier())));
            }
            return rc;
        }

        private boolean isExclusivelyType(String value) {
            Set<String> types = getTypes();
            return types.size()==1 && types.contains(value); 
        }

        /*
         * FIX: Maven seems to allow cyclic dependencies. An example can be found
         *      in com.hp.hpl.jena:arq:2.6.0:pom and com.hp.hpl.jena:jena:2.5.7:pom
         *      which references each other. --mcpat
         */
        private boolean recursion= false;
        public int getRecursiveChildCount() {
            try {
                int rc = children.size();
                if(recursion) {
                    return rc;
                }
                
                recursion= true;
                for (Edge child : children) {
                    int t = child.getRecursiveChildCount();
                    if( t > rc ) {
                        rc = t;
                    }
                }
                return rc;
            } finally {
                recursion= false;
            }
        }

    }

    private class Edge {
        private Node parent;
        private Node child;
        private String scope;
        private boolean optional;
        private DependencyNode dependencyNode;

        public Edge(Node parent, Node child, DependencyNode dependencyNode) {
            this.parent = parent;
            this.child = child;
            this.dependencyNode = dependencyNode;
            this.scope = dependencyNode.getArtifact().getScope();
            this.optional = dependencyNode.getArtifact().isOptional();
        }
        public Edge(Edge edge) {
            this.parent = edge.parent;
            this.child = edge.child;
            this.dependencyNode = edge.dependencyNode;
            this.scope = edge.scope;
            this.optional = edge.optional;
        }

        public Edge optional(boolean optional) {
            if ( this.optional == optional) {
                return this;
            }
            Edge rc =  new Edge(this);
            rc.optional = optional;
            return rc;
        }
        
        public Edge scope(String scope) {
            if ( this.scope.equals(scope) ) {
                return this;
            }
            Edge rc =  new Edge(this);
            rc.scope = scope;
            return rc;
        }

        public boolean isHidden() {
            if( hideTransitive && dependencyNode.getParent().getParent()!=null ) {
                return true;
            }
            if(hideOptional && optional)
                return true;
            if(hideScopes.contains(scope) )
                return true;
            
            final int state = dependencyNode.getState();
            if(hideOmitted && (state==DependencyNode.OMITTED_FOR_CONFLICT || state==DependencyNode.OMITTED_FOR_CYCLE) ) {
                return true;
            }
            return false;
        }

        public boolean isScope(String s) {
            return scope.equals(s);
        }

        public String getLineStyle() {
            if( optional ) {
                return "dotted";
            }
            return "solid";
        }

        public String getLabel() {
            StringBuilder sb = new StringBuilder();
            if ( !isScope("compile")) {
                sb.append(scope);
            }
            if ( optional ) {
                if( sb.length()!=0 ) {
                    sb.append(",");
                }
                sb.append("optional");
            }
            return sb.toString();
        }

        public String getColor() {
            if (isScope("test")) {
                return "cornflowerblue";
            }
            return "black";
        }

        double getWeight() {
            double rc = 1 + getRecursiveChildCount();
            if ( isScope("compile")) {
                rc *= 2;
            }
            if ( !optional ) {
                rc *= 2;
            }
            return rc;
        }

        private int getRecursiveChildCount() {
            return child.getRecursiveChildCount();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Edge edge = (Edge) o;

            if (parent != null ? !parent.equals(edge.parent) : edge.parent != null) return false;
            if (child != null ? !child.equals(edge.child) : edge.child != null) return false;
            if (scope != null ? !scope.equals(edge.scope) : edge.scope != null) return false;
            if (optional != edge.optional) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = parent != null ? parent.hashCode() : 0;
            result = 31 * result + (child != null ? child.hashCode() : 0);
            result = 31 * result + (scope != null ? scope.hashCode() : 0);
            result = 31 * result + (optional ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Edge{" +
                    "parent=" + parent +
                    ", child=" + child +
                    ", scope='" + scope + '\'' +
                    ", optional=" + optional +
                    '}';
        }
    }

    public void add(DependencyNode dn) {
        add(dn, true);
    }

    private Node add(DependencyNode dn, boolean root) {
        Node parent = getNode(dn);
        if( root ) {
            parent.roots++;
        }
        if (dn.hasChildren()) {
            for (DependencyNode c : (List<DependencyNode>) dn.getChildren()) {
                Node child = add(c, false);
                Edge edge = new Edge(parent, child, c);
                add(edge);
            }
        }
        return parent;
    }

    private Node getNode(DependencyNode dn) {
        Artifact artifact = dn.getArtifact();
        String id = artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion();
        if( artifact.getClassifier()!=null ) {
            id += ":"+artifact.getClassifier();
        }
        Node node = nodes.get(id);
        if (node == null) {
            node = new Node(id, dn.getArtifact());
            nodes.put(id, node);
        }
        return node;
    }

    private void add(Edge edge) {
        if (edges.add(edge)) {
            edge.child.parents.add(edge);
            edge.parent.children.add(edge);
        }
    }

    private void remove(Node node) {
        nodes.remove(node.getId());

        // Remove the edges attached to this node...
        for (Edge edge : new ArrayList<Edge>(node.parents)) {
            remove(edge);
        }
        for (Edge edge : new ArrayList<Edge>(node.children)) {
            remove(edge);
        }
    }

    private void remove(Edge edge) {
        edge.parent.children.remove(edge);
        edge.child.parents.remove(edge);
        edges.remove(edge);
    }

    public void export(File target) throws MojoExecutionException {

        // Drop nodes and edges which are hidden...
        for (Node node : new ArrayList<Node>(nodes.values()) ) {
            if (node.isHidden()) {
                log.debug("Dropping hidden node: "+node);
                remove(node);
            }
        }
        for (Edge edge : new ArrayList<Edge>(edges) ) {
            if (edge.isHidden()) {
                log.debug("Dropping hidden edge: "+edge);
                remove(edge);
            }

        }

        if ( cascade ) {
            // Propagate the attributes down to the children.

            LinkedList<Node> ll = new LinkedList<Node>(nodes.values());
            while( !ll.isEmpty() ) {
                // Optional propagates...
                Node node = ll.removeFirst();
                if( node.isOptional() )  {
                    for (Edge edge : new ArrayList<Edge>(node.children)) {
                        if( !edge.optional ) {
                            remove(edge);
                            add(edge.optional(true));

                            // If a child filpped to optional.. then we need
                            // to enqueue so we process it's children
                            if( edge.child.isOptional() ) {
                                ll.addLast(edge.child);
                            }
                        }
                    }
                }

                // scope propagates....
                if( node.isScope("test") )  {
                    for (Edge edge : new ArrayList<Edge>(node.children)) {
                        if( !edge.isScope("test") ) {
                            remove(edge);
                            add(edge.scope("test"));

                            // If a child filpped to test.. then we need
                            // to enqueue so we process it's children
                            if( edge.child.isScope("test") ) {
                                ll.addLast(edge.child);
                            }
                        }
                    }
                }
            }
        }

        // Remove all the non root nodes that are disconnected.
        for (Node node : new ArrayList<Node>(nodes.values()) ) {
            if (node.parents.size()==0 && node.roots==0) {
                log.debug("Dropping orphaned node: "+node);
                remove(node);
            }
        }

        // Write the source file...
        boolean convertDotFile=true;
        File source = new File(target.getParentFile(), target.getName() + ".dot");

        // User might just be requesting a dot file..
        if( target.getName().endsWith(".dot") ) {
            convertDotFile = false;
            source = target;
        }

        PrintStream os = null;
        try {
            log.debug("Exporting to: "+source);
            os = new PrintStream(source);
            DotExporter exporter = new DotExporter(os);
            exporter.export();
        } catch (Exception e) {
            throw new MojoExecutionException("Could not create the dot file used to generate the image.", e);
        } finally {
            os.close();
        }


        if (!convertDotFile) {
            return;
        }
        
        try {
            Commandline commandline = new Commandline();
            try {
                commandline.addSystemEnvironment();
            } catch (Exception ignore) {
            }
            commandline.setExecutable("dot");
            
            ArrayList<String> cmdArgs= new ArrayList<String>(5);
            if(urlCreator != null) {
                File mapFile= new File(target.getParentFile(), target.getName() + ".map");
                cmdArgs.add("-Tcmapx");
                cmdArgs.add("-o" + mapFile.getAbsolutePath());
            }
            
            cmdArgs.add("-T" + FileUtils.getExtension(target.getName()));
            cmdArgs.add("-o" + target.getAbsolutePath());
            cmdArgs.add(source.getAbsolutePath());
            commandline.addArguments(cmdArgs.toArray(new String[cmdArgs.size()]));

            log.debug("Executing dot command...");
            int rc = CommandLineUtils.executeCommandLine(commandline, new DefaultConsumer(), new DefaultConsumer());
            if (rc != 0) {
                throw new MojoExecutionException("Execution of the 'dot' command failed.  Perhaps it's not installed.  See: http://www.graphviz.org/");
            }
            log.debug("Graph generated. ");
            if( !keepDot ) {
                source.delete();
            }

        } catch (CommandLineException e) {
            throw new MojoExecutionException("Execution of the 'dot' command failed.", e);
        }

    }

    private class DotExporter {
        private final PrintStream out;
        int indent = 0;

        public DotExporter(PrintStream os) {
            this.out = os;
        }

        public void export() {
        	
            String graphFont = "Serif";
            String nodeFont = "SanSerif";
            
            String osName = System.getProperty("os.name", "NO OS NAME!!");
            if (osName.contains("Windows")) {
                graphFont = "arial";
                nodeFont = "arial";
            }
        	
            p("digraph dependencies {").i(1);
            {
                p("graph [").i(1);
                {
                    if (label != null) {
                        p("label=" + q(label));
                    }
                    p("labeljust=l");
                    p("labelloc=t");
                    p("fontsize=18");
                    p("fontname="+q(graphFont));
                    p("ranksep=1");
                    p("rankdir="+q(direction));
                    p("nodesep=.05");

                }
                i(-1).p("];");
                p("node [").i(1);
                {
                    p("fontsize=8");
                    p("fontname="+q(nodeFont));
                    p("shape=\"rectangle\"");
                }
                i(-1).p("];");
                p("edge [").i(1);
                {
                    p("fontsize=8");
                    p("fontname="+q(nodeFont));
                }
                i(-1).p("];");

                // Write the nodes..
                for (Node node : nodes.values()) {
                    p(q(node.getId()) + " [").i(1);
                    {
                        p("fontsize="+node.getFontSize());
                        p("label=" + q(node.getLabel()));
                        p("color=" + q(node.getColor()));
                        p("fontcolor=" + q(node.getFontColor()));
                        p("fillcolor=" + q(node.getFillColor()));
                        p("style=" + q(node.getLineStyle()));
                        
                        String url= node.getURL();
                        if(url != null) {
                            p("URL=" + q(url));
                        }
                    }
                    i(-1).p("];");
                }

                // Write the edges..
                for (Edge edge : edges) {
                    p(q(edge.parent.getId()) + " -> " + q(edge.child.getId()) + " [").i(1);
                    {
                        p("label=" + q(edge.getLabel()));
                        p("style=" + q(edge.getLineStyle()));
                        p("color=" + q(edge.getColor()));
                        p("fontcolor=" + q(edge.getColor()));
                        p("weight=" + edge.getWeight());
                    }
                    i(-1).p("];");
                }

            }
            i(-1).p("}");
        }

        private String q(String value) {
            return "\"" + value + "\"";
        }

        private DotExporter i(int indent) {
            this.indent += indent;
            return this;
        }

        private DotExporter p(String x) {
            for (int i = 0; i < indent; i++) {
                out.print("  ");
            }
            out.println(x);
            return this;
        }

    }


}