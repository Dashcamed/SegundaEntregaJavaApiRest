package SegundaEntrega.api.services;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import SegundaEntrega.api.DTO.ClienteDTO;
import SegundaEntrega.api.mapper.ClienteMapper;
import SegundaEntrega.api.model.Cliente;
import SegundaEntrega.api.model.ClientePanaderia;
import SegundaEntrega.api.model.Panaderia;
import SegundaEntrega.api.repository.ClienteRepository;
import SegundaEntrega.api.repository.PanaderiaRepository;
import jakarta.transaction.Transactional;

@Service
public class ClienteServiceRest {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com/users";

    @Autowired
    private final ClienteRepository clienteRepository;
    @Autowired
    private final PanaderiaRepository panaderiaRepository;
    @Autowired
    private final ClienteMapper clienteMapper;
    @Autowired
    private RestTemplate restTemplate;

    public ClienteServiceRest(ClienteRepository clienteRepository, PanaderiaRepository panaderiaRepository, ClienteMapper clienteMapper) {
        this.clienteRepository = clienteRepository;
        this.panaderiaRepository = panaderiaRepository;
        this.clienteMapper = clienteMapper;
    }

    // Obtener todos los clientes desde la base de datos y/o API externa
    public List<ClienteDTO> getAllClients() {
        List<ClienteDTO> clientesDB = clienteRepository.findAll().stream()
                .map(clienteMapper::toDTOCliente)
                .collect(Collectors.toList());

        // Obtener clientes desde una API externa utilizando RestTemplate
        Cliente[] clientesAPI = restTemplate.getForObject(BASE_URL, Cliente[].class);

        if (clientesAPI != null) {
            for (Cliente cliente : clientesAPI) {
                clientesDB.add(clienteMapper.toDTOCliente(cliente));
            }
        }

        return clientesDB;
    }

    // Obtener cliente por ID, buscando primero en la base de datos y luego en la API externa
    public ClienteDTO getClientById(Long id) {
        Optional<Cliente> optionalClient = clienteRepository.findById(id);

        if (optionalClient.isPresent()) {
            return clienteMapper.toDTOCliente(optionalClient.get());
        } else {
            // Si el cliente no está en la base de datos, buscar en la API externa
            ClienteDTO clienteDTO = restTemplate.getForObject(BASE_URL + "/{id}", ClienteDTO.class, id);
            if (clienteDTO != null) {
                return clienteDTO;
            } else {
                throw new RuntimeException("Cliente no encontrado ni en la base de datos ni en la API externa");
            }
        }
    }

    @Transactional
    public ClienteDTO saveClientFromApi(Long id) {
        // Obtener el cliente desde la API
        ClienteDTO clienteDTO = restTemplate.getForObject(BASE_URL + "/{id}", ClienteDTO.class, id);

        if (clienteDTO != null) {
            // Mapea el DTO a la entidad Cliente
            Cliente cliente = clienteMapper.toEntity(clienteDTO);

            // Si la panadería existe, la asignamos al cliente
            if (clienteDTO.getPanaderiaId() != null) {
                Optional<Panaderia> optionalPanaderia = panaderiaRepository.findById(clienteDTO.getPanaderiaId());
                if (optionalPanaderia.isPresent()) {
                    ClientePanaderia clientePanaderia = new ClientePanaderia(cliente, optionalPanaderia.get());
                    cliente.getClientePanaderias().add(clientePanaderia);
                } else {
                    throw new RuntimeException("Panadería no encontrada con ID: " + clienteDTO.getPanaderiaId());
                }
            }

            // Guarda el cliente en la base de datos
            Cliente savedCliente = clienteRepository.save(cliente);
            return clienteMapper.toDTOCliente(savedCliente);
        } else {
            throw new RuntimeException("Cliente no encontrado en la API con ID: " + id);
        }
    }

    @Transactional
    public ClienteDTO saveClientFromDTO(ClienteDTO clienteDTO) {
        // Mapea el DTO a la entidad Cliente
        Cliente cliente = clienteMapper.toEntity(clienteDTO);

        // Si tiene una panadería asociada, la buscamos y la asociamos al cliente
        if (clienteDTO.getPanaderiaId() != null) {
            Optional<Panaderia> optionalPanaderia = panaderiaRepository.findById(clienteDTO.getPanaderiaId());
            if (optionalPanaderia.isPresent()) {
                ClientePanaderia clientePanaderia = new ClientePanaderia(cliente, optionalPanaderia.get());
                cliente.getClientePanaderias().add(clientePanaderia);
            } else {
                throw new RuntimeException("Panadería no encontrada con ID: " + clienteDTO.getPanaderiaId());
            }
        }

        // Guarda el cliente en la base de datos
        Cliente savedCliente = clienteRepository.save(cliente);
        return clienteMapper.toDTOCliente(savedCliente);
    }

    // Actualizar un cliente existente en la base de datos y en la API externa
    @Transactional
    public ClienteDTO updateClient(Long id, ClienteDTO clienteDTO) {
        Optional<Cliente> optionalClient = clienteRepository.findById(id);

        if (optionalClient.isPresent()) {
            Cliente existingClient = optionalClient.get();

            // Actualizar datos del cliente
            existingClient.setName(clienteDTO.getName());
            existingClient.setEmail(clienteDTO.getEmail());
            existingClient.setPhone(clienteDTO.getPhone());
            existingClient.setFechaDeModificacion(FechaService.getFechaActual());
            existingClient.getClientePanaderias().clear();

            if (clienteDTO.getPanaderiaId() != null) {
                Optional<Panaderia> optionalPanaderia = panaderiaRepository.findById(clienteDTO.getPanaderiaId());
                if (optionalPanaderia.isPresent()) {
                    ClientePanaderia clientePanaderia = new ClientePanaderia(existingClient, optionalPanaderia.get());
                    existingClient.getClientePanaderias().add(clientePanaderia);
                } else {
                    throw new RuntimeException("Panadería no encontrada con ID: " + clienteDTO.getPanaderiaId());
                }
            }

            Cliente updatedClient = clienteRepository.save(existingClient);

            // Actualizar el cliente en la API externa
            restTemplate.put(BASE_URL + "/{id}", clienteDTO, id);

            return clienteMapper.toDTOCliente(updatedClient);

        } else {
            throw new RuntimeException("Cliente no encontrado con ID: " + id);
        }
    }

    // Eliminar un cliente en la base de datos y en la API externa
    public void deleteClient(Long id) {
        if (clienteRepository.existsById(id)) {
            clienteRepository.deleteById(id);

            // Eliminar el cliente en la API externa
            restTemplate.delete(BASE_URL + "/{id}", id);
        } else {
            throw new RuntimeException("Cliente no encontrado con ID: " + id);
        }
    }
}