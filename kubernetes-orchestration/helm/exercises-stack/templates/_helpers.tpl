{{- define "exercises.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "exercises.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "exercises.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "exercises.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end }}

{{- define "exercises.labels" -}}
helm.sh/chart: {{ include "exercises.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- if .Values.global.region }}
region: {{ .Values.global.region | quote }}
{{- end }}
{{- if .Values.global.platform }}
platform: {{ .Values.global.platform | quote }}
{{- end }}
{{- if .Values.global.cluster }}
cluster: {{ .Values.global.cluster | quote }}
{{- end }}
{{- end }}
