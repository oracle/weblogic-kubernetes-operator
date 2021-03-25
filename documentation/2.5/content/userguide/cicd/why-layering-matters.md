---
title: "Why layering matters"
date: 2019-04-11T13:27:58-04:00
draft: false
weight: 2
description: "Learn why Docker image layering affects CI/CD processes."
---


#### How does layering affect our CI/CD process?

Now that we know more about layering, let’s talk about why it is important to our
CI/CD process.  Let's consider the kinds of updates we might want to make to our domain:

{{< img "Types of updates" "images/updates.png" >}}

You might want to update the domain by:

- Installing a patch on the operating system or a library.
- Updating the version of the JDK you are using.
- Picking up a new version of WebLogic Server.
- Installing patches on WebLogic Server.
- Updating the domain configuration, for example:
  - Adding or changing a resource like a data source or queue.
  - Installing or updating applications.
  - Changing various settings in the domain configuration.

If we just want to update the domain configuration itself, that is the top layer,
then it is pretty easy.  We can make the necessary changes and save a new version
of that layer, and then roll the domain.  We could also choose to just build another
layer on top of the existing top layer that contains our delta.  If the change is
small, then we will just end up with another small layer, and as we have seen,
the small layers are no problem.

But consider a more complicated scenario - let's take updating the JDK as an example
to understand the impact of layers.  Say we want to update from JDK 8u201 to 8u202
as shown in the example above.  If we took the "your first domain" image and updated
the JDK, then we would end up with a new layer on top containing JDK 8u202.  That
other layer with JDK 8u201 is still there; even if we "delete" the directory, we
don't get that space back.  So now our 1.5GB "image" has grown to 1.75GB.  This is
not ideal, and the more often we try to change lower layers, the worse it gets.  

You might be asking, "Can't we just swap out the JDK layer for a new one?"  That is
an excellent question, but the unfortunate reality today is that there is no reliable
way to do that.  There are various attempts to create a "rebasing" capability for
Docker that would enable such an action, but some research will show you that they
are mostly abandoned due to limited documentation of how the layering works at the
level of detail needed to implement something like this.

Next you might think, "Oh, that’s ok, we can just rebuild the layers above the JDK
on top of this new layer."  That is very true, we can.  But there is a big caveat
here.  When you create a WebLogic domain, a domain encryption key is created.  This
key is stored in the `security/SerializedSystemIni.dat` file in your domain and it
is used to encrypt several other things in your domain configuration, like passwords,
for example.  Today (in WebLogic Server 12.2.1.3.0) there is no way to conveniently
"extract" or "reuse" this encryption key.  So what does this mean in practice?


> If you recreate the domain in your CI/CD process, even though you may end up with
a domain that is for all intents and purposes identical to the previous domain, it
will have a different encryption key.

This means that technically it is a "different" domain.  Does this matter?  Maybe,
maybe not.  It depends.  If you want to do a rolling restart of your domain, then
yes, it matters.  First of all, the "new" servers will fail to start because the
operator will be trying to inject credentials to start the server which were
encrypted with the "old" domain encryption key.

But even if this did not prevent them from starting, there would still be a problem.  
You cannot have members of a domain with different encryption keys.  If WebLogic
saw a new member trying to join the domain with a different key, it would consider
it to be an intruder and refuse to accept it into the domain.  Client HTTP sessions
would not work across the two different sets of servers, so clients could see errors
and need to retry.  Worse, if these two different sets of servers tried to access
the same resources this could lead to data corruption.

So what can we do?  Well, we could not roll the domain, but instead completely shut down the
old version first, and then start up the new one.  This way we avoid any
issues with incompatibilities, but we do introduce a brief outage.  This may be
acceptable, or it may not.

Another option is to find a way to keep the "same" domain, that is, the same domain
encryption key, so that we can still roll the domain and there will be no conflicts.

#### Mutating the domain configuration without losing the encryption keys

If we want to make a change in a lower layer without losing our domain encryption
keys, then we need to find a way to "save" the domain and then put it back into a
new layer, later, on top of the other new (lower) layers, as depicted in the image below:

{{< img "Rebuilding layers" "images/rebuild.png" >}}

The process looks like this:

- From our existing image (left), we extract the domain into some kind of archive.
- Then we start with the new JDK image which was built on top of the same base
  image (or we build it ourselves, if needed).
- We build a new WebLogic layer (or grab the one that Oracle built for us) on
  top of this new JDK.
- Then we need to “restore” our domain from the archive into a new layer.
