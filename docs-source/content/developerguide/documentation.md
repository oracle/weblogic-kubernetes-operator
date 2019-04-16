---
title: "Documentation"
date: 2019-03-19T07:13:22-04:00
draft: false
weight: 9
---

This documentation is produced using [Hugo](http://gohugo.io).  To make an
update to the documentation, follow this process:

1. If you have not already done so, clone the repository.

    ```
    git clone https://github.com/oracle/weblogic-kubernetes-operator
    ```

2. Create a new branch from master.

    ```
    git checkout master
    git pull origin master
    git checkout -b your-branch
    ```

3. Make your documentation updates by editing the source files in
`docs-source/content`.
{{% notice note %}}
Make sure you _only_ check in the changes from the `docs-source/content` area;
do not build the site and check in the static files.
{{% /notice %}}

4. If you wish to view your changes, you can run the site locally using
these commands; the site will be available on the URL shown here:

    ```
    cd docs-source
    hugo server -b http://localhost:1313/weblogic-kubernetes-operator
    ```

5. When you are ready to submit your changes, push your branch to `origin`
and submit a pull request. Remember to follow the guidelines in the
[CONTRIBUTING](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/CONTRIBUTING.md)
document.
