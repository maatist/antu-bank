# ADR-0009: Despliegue doble track (público always-on + AWS on-demand)

- **Estado:** Aceptado
- **Fecha:** consolidado desde el diseño (sección 12, ADR-009)

## Contexto

Antu Bank es, ante todo, un **proyecto de portafolio**. De ahí surgen dos objetivos en tensión:

1. Un reclutador debe poder **abrir una URL y probar la aplicación en minutos, a cualquier hora**,
   sin instalar nada. Esto exige una demo pública encendida 24/7.
2. El autor quiere **demostrar skill de cloud e IaC serio** (Kubernetes, Terraform, servicios
   gestionados de AWS). Mantener un cluster EKS + RDS + MSK encendido de forma permanente sería
   caro e injustificable para una demo.

Un solo entorno no satisface ambos objetivos: lo barato y siempre encendido no luce como cloud
serio, y lo que luce como cloud serio no puede estar siempre encendido sin costo.

## Decisión

Adoptar un **despliegue de doble track**:

- **Track público always-on:** frontend en **Vercel** + backend en un PaaS de contenedores
  gratuito/barato (**Render/Fly.io**), con el perfil `demo` reducido (ver
  [ADR-0013](0013-perfil-demo-schema-por-servicio.md)). Accesible 24/7 vía HTTPS, sin costo
  permanente relevante.
- **Track AWS on-demand:** **Terraform** que levanta **EKS + RDS + MSK** y **Helm** que despliega
  la app, junto con una **guía de levantar/derribar** para evitar costos. Se enciende para demostrar
  la capacidad cloud y se derriba cuando no se usa.

## Consecuencias

**Beneficios**

- El reclutador tiene acceso inmediato 24/7 a una demo funcional, sin fricción.
- Se conserva **evidencia de skill cloud** (IaC completa de EKS/RDS/MSK y Helm) sin pagar por
  infraestructura encendida permanentemente.
- Cada track usa la herramienta adecuada a su objetivo.

**Trade-offs**

- Hay que **mantener dos configuraciones de despliegue** (el perfil demo reducido y el stack AWS
  completo), con el esfuerzo de que ambas sigan funcionando a medida que el proyecto evoluciona.
- El track AWS requiere disciplina de teardown para no incurrir en costos; de ahí la guía de ciclo
  de vida.

El doble esfuerzo de mantenimiento es el precio de cumplir dos objetivos legítimamente distintos
del portafolio.
