---
title: "Documentation"
date: 2019-03-19T07:13:22-04:00
draft: false
weight: 9
---

This documentation is produced using [Hugo](http://gohugo.io).  To make an 
update to the documentation, follow this process:

1. Clone the repository if you have not already

    ```
    git clone https://github.com/oracle/weblogic-kubernetes-operator
    ```

2. Create a new branch from master

    ```
    git checkout master
    git pull origin master
    git checkout -b your-branch
    ```

3. Make your documentation updates by editing the source files in 
`docs-source/content`.

4. If you wish to view your changes you can run the site locally using 
these commands; the site will be available on the URL shown here:

    ```
    cd docs-source
    hugo server -b http://localhost:1313/weblogic-kubernetes-operator
    ```

5. When you are ready to submit your changes, push your branch to `origin`
and submit a pull request. Remember to follow the guidelines in the 
[CONTRIBUTING](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/CONTRIBUTING.md)
document.

{{% notice note %}}
Make sure you only check in your source code changes in `doc-source`; do 
not build the site and check in the static files.
{{% /notice %}}
