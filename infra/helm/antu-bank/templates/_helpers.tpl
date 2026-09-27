{{/*
=============================================================================
Helpers compartidos del chart antu-bank.
Definen nombres, labels y utilidades reutilizadas por todas las plantillas.
=============================================================================
*/}}

{{/*
Nombre base del chart (recortado a 63 chars, límite de nombres de K8s).
*/}}
{{- define "antu-bank.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Nombre "fullname" del release: <release>-<chart>, o fullnameOverride si se define.
Se usa como prefijo de los recursos para evitar colisiones entre releases.
*/}}
{{- define "antu-bank.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Etiqueta chart (nombre-versión saneado).
*/}}
{{- define "antu-bank.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Labels comunes a todos los recursos del chart.
Uso: {{- include "antu-bank.labels" . | nindent 4 }}
*/}}
{{- define "antu-bank.labels" -}}
helm.sh/chart: {{ include "antu-bank.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: antu-bank
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
{{- end -}}

{{/*
-----------------------------------------------------------------------------
Helpers "de servicio": se invocan con un dict {"root": $, "name": <svc>, "svc": <config>}
para renderizar recursos por cada microservicio del map .Values.services.
-----------------------------------------------------------------------------
*/}}

{{/*
Nombre completo de un microservicio: <fullname>-<svcName>.
*/}}
{{- define "antu-bank.service.fullname" -}}
{{- printf "%s-%s" (include "antu-bank.fullname" .root) .name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Labels selectores (estables) de un microservicio.
*/}}
{{- define "antu-bank.service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "antu-bank.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .name }}
{{- end -}}

{{/*
Labels completos de un microservicio (comunes + selectores).
*/}}
{{- define "antu-bank.service.labels" -}}
{{ include "antu-bank.labels" .root }}
{{ include "antu-bank.service.selectorLabels" . }}
{{- end -}}

{{/*
Referencia de imagen completa: <registry>/<repository>:<tag>.
Precedencia: svc.image.* sobre global .Values.image.*.
El repository puede sobreescribirse por servicio; si no, se usa <repositoryPrefix>/<svcName>.
*/}}
{{- define "antu-bank.service.image" -}}
{{- $img := .root.Values.image -}}
{{- $svcImage := default (dict) .svc.image -}}
{{- $registry := default $img.registry (get $svcImage "registry") -}}
{{- $tag := default $img.tag (get $svcImage "tag") -}}
{{- $repo := "" -}}
{{- if (get $svcImage "repository") -}}
{{- $repo = get $svcImage "repository" -}}
{{- else -}}
{{- $repo = printf "%s/%s" $img.repositoryPrefix .name -}}
{{- end -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" $registry $repo $tag -}}
{{- else -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}
{{- end -}}
