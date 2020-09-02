{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "patient-hiu.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "patient-hiu.fullname" -}}
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
Create chart name and version as used by the chart label.
*/}}
{{- define "patient-hiu.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "patient-hiu.labels" -}}
helm.sh/chart: {{ include "patient-hiu.chart" . }}
{{ include "patient-hiu.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "patient-hiu.selectorLabels" -}}
app.kubernetes.io/name: {{ include "patient-hiu.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "patient-hiu.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (include "patient-hiu.fullname" .) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Create the name of the env to use
*/}}

{{- define "helpers.list-env-variables"}}
{{- $secretName := printf "%s-%s" (include "patient-hiu.fullname" .) "secrets" -}}
{{- range $key, $val := .Values.env.secrets }}
    - name: {{ $key }}
      valueFrom:
        secretKeyRef:
          name: {{ $secretName }}
          key: {{ $key }}
{{- end}}
{{- range $key, $val := .Values.env.normal }}
    - name: {{ $key }}
      value: {{ $val | quote }}
{{- end}}
{{- end }}
