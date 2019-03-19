---
title: "Sample Page"
date: 2019-02-22T16:07:43-05:00
draft: true
weight: 1
---

### Here is some neat stuff we can do

We can draw decision diagrams:

{{<mermaid align="left">}}
graph LR;
    A[Install Operator] -->|Now decide| C{Domain Type}
    C -->|Persistent| D[Create persistent domain]
    C -->|Domain in Image| E[Create domain image]
{{< /mermaid >}}

### And some more

And have expanding more detail thingies

{{%expand "Like this" %}}
Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod
tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam,
quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo
consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse
cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non
proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
{{% /expand%}}

### And also this

{{< youtube B5UmY2xAJnk >}}
