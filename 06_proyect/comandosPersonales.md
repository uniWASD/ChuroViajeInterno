### Ver vinculaciones
```bash
git remote -v
```

### Vincular
```bash
git remote add interno <URL_DEL_REPOSITORIO>
```

### Pullear
```bash
# Muevete a una rama 
git checkout develop

git pull interno develop

# Crear nueva rama si es necesario
git checkout -b feature/HUXX-CUXX-nombre-corto
```

### Pushear
```bash
git add .

git commit -m "feat(modulo): avance de tarea (HU-XX)" -m "Refs #ID"

# 3. Sube tus cambios a tu rama en el repositorio interno
git push interno feature/HUXX-CUXX-nombre-corto
```

### V
```bash

```