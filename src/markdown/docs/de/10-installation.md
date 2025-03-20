# Installation

Docker-Images für ldproxy sind auf [Docker Hub](https://hub.docker.com/r/iide/ldproxy/) verfügbar.

## Vorraussetzungen

Für diese Anleitung benötigen Sie eine Installation von Docker. Docker ist für Linux, Windows und Mac verfügbar. Detaillierte Installationsanleitungen für jede Plattform finden Sie [hier](https://docs.docker.com/).

## Installieren und Starten von ldproxy

Um ldproxy zu installieren, führen Sie einfach den folgenden Befehl auf einem Rechner mit installiertem Docker aus:

```bash
docker run --name ldproxy -d -p 7080:7080 -v ~/ldproxy_data:/data iide/ldproxy:latest
```

Dadurch wird das neueste stabile ldproxy-Image heruntergeladen, als neuer Container bereitgestellt, die Webanwendung auf Port 7080 verfügbar gemacht und die Anwendungsdaten in Ihrem Home-Verzeichnis gespeichert.

Um zu überprüfen, ob der Docker-Prozess läuft, verwenden Sie

```bash
docker ps
```

was etwas Ähnliches zurückgeben sollte wie

```bash
CONTAINER ID        IMAGE                 COMMAND                  CREATED             STATUS              PORTS                    NAMES
62db022d9bee        iide/ldproxy:latest   "/ldproxy/bin/ldproxy"   16 minutes ago      Up 16 minutes       0.0.0.0:7080->7080/tcp   ldproxy
```

Prüfen Sie, ob ldproxy läuft, indem Sie die URI http://localhost:7080/ in einem Webbrowser aufrufen, das sollten die Startseite ohne API sehen.

Falls ldproxy nicht antwortet, konsultieren Sie das Protokoll mit `docker logs ldproxy`.
