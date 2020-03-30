// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.HashMap;

public class Helm {

    private String chart;
    private String name;
    private String namespace;
    private String repoUrl;
    private HashMap<String, String> values;

    private Helm(HelmBuilder builder) {
        this.chart = builder.chart;
        this.name = builder.name;
        this.namespace = builder.namespace;
        this.repoUrl = builder.repoUrl;
        this.values = builder.values;
    }

    public String getChart() {
        return chart;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public HashMap<String, String> getValues() {
        return values;
    }

    public boolean install(){
        return true;
    }

    public boolean upgrade(){
        return true;
    }

    public boolean delete(){
        return true;
    }

    public boolean addRepo() {
        return true;
    }

    public static class HelmBuilder {
        private String chart;
        private String name;
        private String namespace;
        private String repoUrl;
        private HashMap<String, String> values;

        public HelmBuilder(String chart, String name) {
            this.chart = chart;
            this.name = name;
        }
        public HelmBuilder(String repoUrl) {
            this.repoUrl = repoUrl;
        }
        public HelmBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
        public HelmBuilder repoUrl(String repoUrl) {
            this.repoUrl = repoUrl;
            return this;
        }
        public HelmBuilder values(HashMap<String, String> values) {
            this.values = values;
            return this;
        }
        public Helm build() {
            Helm helm = new Helm(this);
            return helm;
        }
    }

}
