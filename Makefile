NAMESPACE_NAME ?= default
RELEASE = top-tweets-web
SELECTOR = release=$(RELEASE)
HELMARGS ?=
TAG ?= latest
IMAGE ?= dlj-docker.freemyip.com:8443/$(RELEASE)

buildjar:
	./gradlew clean build

pushimage:
	./gradlew clean build jib -Djib.to.image=$(IMAGE):$(TAG)

stern:
	stern $(RELEASE) --tail 200

listpods:
	kubectl get pod -l $(SELECTOR)

watchpods:
	watch kubectl get pod -l $(SELECTOR)

install:
	helm upgrade $(RELEASE) $(HELMARGS) ../helmcharts/generic \
	--set image.repository=$(IMAGE) \
	--set image.tag=$(TAG) \
	--set nameOverride=$(RELEASE) \
	--values=values.yaml \
	--install

uninstall:
	helm delete $(RELEASE)

redeploy: TAG:=$(shell uuidgen)
redeploy: pushimage install