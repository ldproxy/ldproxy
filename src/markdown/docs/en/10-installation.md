# Installation

Docker images for ldproxy are available on [Docker Hub](https://hub.docker.com/r/iide/ldproxy/).

## Prerequisites

For this guide, you will need an installation of Docker. Docker is available for Linux, Windows and Mac. You will find detailed installation guides for each platform [here](https://docs.docker.com/).

## Installing and starting ldproxy

To install ldproxy, just run the following command on a machine with Docker installed:

```bash
docker run --name ldproxy -d -p 7080:7080 -v ~/ldproxy_data:/data iide/ldproxy:latest
```

This will download the latest stable ldproxy image, deploy it as a new container, make the web application available at port 7080 and save the application data in your home directory.

To check that the docker process is running, use

```bash
docker ps
```

which should return something similar to

```bash
CONTAINER ID        IMAGE                 COMMAND                  CREATED             STATUS              PORTS                    NAMES
62db022d9bee        iide/ldproxy:latest   "/ldproxy/bin/ldproxy"   16 minutes ago      Up 16 minutes       0.0.0.0:7080->7080/tcp   ldproxy
```

Check that ldproxy is running by opening the URI http://localhost:7080/ in a web browser, which should open the API catalog page (with no API).

If ldproxy is not responding, consult the log with `docker logs ldproxy`.
