package cl.banco.clientes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Gestion de clientes: informacion personal y perfiles. */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {
  private final ClienteRepository repo;
  public ClienteController(ClienteRepository repo) { this.repo = repo; }

  @GetMapping public List<Cliente> listar() { return repo.findAll(); }

  @GetMapping("/{id}")
  public Cliente obtener(@PathVariable Long id) {
    return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no existe"));
  }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Cliente crear(@RequestBody Cliente c) {
    if (c.rut == null || c.nombre == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rut y nombre son obligatorios");
    if (repo.existsByRut(c.rut)) throw new ResponseStatusException(HttpStatus.CONFLICT, "RUT ya registrado");
    c.id = null;
    return repo.save(c);
  }

  @PutMapping("/{id}")
  public Cliente actualizar(@PathVariable Long id, @RequestBody Cliente n) {
    Cliente c = obtener(id);
    if (n.nombre != null) c.nombre = n.nombre;
    if (n.email != null) c.email = n.email;
    if (n.telefono != null) c.telefono = n.telefono;
    return repo.save(c);
  }
}
