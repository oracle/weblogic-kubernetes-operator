Update/remove – done by the operator script
This documentation describes the setup for configuring the Traefik Ingress Controller to perform load balancing across managed servers in a domain. Traefik documentation can be found at: https://docs.traefik.io/user-guide/kubernetes/
Setup Role Based Access Controls
Creating Role Based Access Controls (RBAC) authorizes Traefik to use the Kubernetes API using ClusterRole and ClusterRoleBinding resources.
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: traefik-ingress-controller
rules:
  - apiGroups:
      - ""
    resources:
      - services
      - endpoints
      - secrets
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - extensions
    resources:
      - ingresses
    verbs:
      - get
      - list
      - watch
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: traefik-ingress-controller
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: traefik-ingress-controller
subjects:
- kind: ServiceAccount
  name: traefik-ingress-controller
  namespace: kube-system
kubectl apply -f qa/traefik-rbac.yaml
Deploy the Ingress Controller
The deployment of the Ingress Controller creates a Deployment, Service, and ServiceAccount resources. These resources are all created in the kube-system namespace.
The Service will expose two NodePorts which allow access to the ingress (30305) and admin UI (30315).
apiVersion: v1
kind: ServiceAccount
metadata:
  name: traefik-ingress-controller
  namespace: kube-system
---
kind: Deployment
apiVersion: extensions/v1beta1
metadata:
  name: traefik-ingress-controller
  namespace: kube-system
  labels:
    k8s-app: traefik-ingress-lb
spec:
  replicas: 1
  selector:
    matchLabels:
      k8s-app: traefik-ingress-lb
  template:
    metadata:
      labels:
        k8s-app: traefik-ingress-lb
        name: traefik-ingress-lb
    spec:
      serviceAccountName: traefik-ingress-controller
      terminationGracePeriodSeconds: 60
      containers:
      - image: traefik:1.3.1
        name: traefik-ingress-lb
        args:
        - --web
        - --kubernetes
---
kind: Service
apiVersion: v1
metadata:
  name: traefik-ingress-service
  namespace: kube-system
spec:
  selector:
    k8s-app: traefik-ingress-lb
  ports:
    - protocol: TCP
      port: 80
      name: web
      nodePort: 30305
    - protocol: TCP
      port: 8080
      name: admin
      nodePort: 30315
  type: NodePort
kubectl apply -f qa/traefik-deployment.yaml
Launch the Traefik admin UI
To launch the Traefik Admin UI enter http://<host-name>:30315.
Load balancing of managed servers
To load balance across managed servers you must create an ingress resource for use with the Traefik Ingress Controller.
The ingress defines the managed server services and their service ports that are exposed via the Traefik Ingress Controller service.
Here is the Ingress resource that is defined in qa/start-domain.yaml.
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: domain1-cluster1
  annotations:
    kubernetes.io/ingress.class: traefik
spec:
  rules:
  - http:
      paths:
      - path: /
        backend:
          serviceName: domain1-managed-server1
          servicePort: 8001
      - path: /
        backend:
          serviceName: domain1-managed-server2
          servicePort: 8001
