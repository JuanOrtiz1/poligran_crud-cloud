import { useEffect, useState } from 'react';
import { request } from './api';

export default function App() {
  const [schema, setSchema] = useState(null);
  const [rows, setRows] = useState([]);
  const [cursor, setCursor] = useState(null);
  const [history, setHistory] = useState([null]);
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState({});
  const [editing, setEditing] = useState(null);
  const [query, setQuery] = useState('');
  const [found, setFound] = useState(null);
  const [file, setFile] = useState(null);
  const [delimiter, setDelimiter] = useState(',');
  const [summary, setSummary] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [connected, setConnected] = useState(false);
  const ready = Boolean(schema?.idField);
  const fields = schema?.fields || [];
  const idOf = row => row[schema.idField];
  const urlId = id => encodeURIComponent(id);

  async function load(at = null) {
    const result = await request(`/registros?limit=10${at ? `&cursor=${urlId(at)}` : ''}`);
    setRows(result.items); setCursor(result.nextCursor); setConnected(true);
  }
  async function refresh() {
    await load(); setHistory([null]); setPage(0); setFound(null);
  }
  async function action(task) {
    setBusy(true); setError(''); setNotice('');
    try { await task(); } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const result = await request('/esquema');
        if (!active) return;
        setSchema(result);
        if (result.idField) await load();
      } catch (e) { if (active) setError(e.message); }
      finally { if (active) setBusy(false); }
    })();
    return () => { active = false; };
  }, []);
  async function submit(e) {
    e.preventDefault();
    await action(async () => {
      const body = Object.fromEntries(fields.map(f => [f.name, form[f.name] ?? '']));
      await request(`/registros${editing === null ? '' : `/${urlId(editing)}`}`, { method: editing === null ? 'POST' : 'PUT', body });
      setNotice(editing === null ? 'Registro creado.' : 'Registro actualizado.');
      setForm({}); setEditing(null); await refresh();
    });
  }
  function edit(row) {
    setEditing(idOf(row)); setForm(Object.fromEntries(Object.entries(row).map(([k, v]) => [k, v === null ? '' : String(v)])));
    document.getElementById('registro-form').scrollIntoView({ behavior: 'smooth' });
  }
  async function navigate(direction) {
    await action(async () => {
      const target = page + direction;
      const at = direction === 1 ? cursor : history[target];
      await load(at);
      if (direction === 1) setHistory([...history.slice(0, page + 1), at]);
      setPage(target);
    });
  }
  function rowActions(row) {
    return <div className="actions"><button className="secondary" disabled={busy} onClick={() => edit(row)}>Editar</button><button className="danger" disabled={busy} onClick={() => setDeleting(idOf(row))}>Eliminar</button></div>;
  }
  return <main>
    <header><div className="eyebrow">POLIGRAN / CLOUD U4</div><h1>Gestor de registros</h1><p>Consulta tu base, administra registros e importa archivos CSV.</p><span className="badge">{connected ? 'Firestore consultado' : 'Conexión por comprobar'} · API Java</span></header>
    {error && <div role="alert" className="message error">{error}</div>}
    {notice && <div role="status" className="message success">{notice}</div>}
    {busy && <p role="status" className="loading">Procesando…</p>}
    {!busy && !ready && <section className="message pending"><h2>API no disponible</h2><p>Inicia el backend y vuelve a cargar la página para obtener el formulario de registros.</p></section>}
    <div className="columns">
      <section className="card"><span className="step">01 / IMPORTAR</span><h2>Cargar un CSV</h2><p className="muted">UTF-8, con los diez encabezados del formulario. Reimportar filas idénticas omite las ya cargadas. Una fila modificada se considera un nuevo registro.</p><p><a href="/plantilla.csv" download>Descargar plantilla CSV</a></p>
        <form onSubmit={e => { e.preventDefault(); action(async () => { setSummary(null); const body = new FormData(); body.append('file', file); body.append('delimiter', delimiter); const result = await request('/registros/importar', { method: 'POST', body }); setSummary(result); await refresh(); }); }}>
          <fieldset disabled={busy || !ready}><label>Archivo CSV<input type="file" accept=".csv,text/csv" required onChange={e => { setFile(e.target.files[0] || null); setSummary(null); }}/></label>
          <label>Delimitador<select value={delimiter} onChange={e => setDelimiter(e.target.value)}><option value=",">Coma (,)</option><option value=";">Punto y coma (;)</option></select></label>
          <button disabled={!file}>Importar registros</button></fieldset>
        </form>
        {summary && <div role="status" className="import-result"><h3>Resultado de la importación</h3><dl className="stats">{[['Leídas',summary.read],['Creadas',summary.created],['Omitidas',summary.skipped],['Fallidas',summary.failed]].map(([label,value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
          <p className="muted">Las filas creadas tienen escritura confirmada. Si una fila falló, reimportar el mismo contenido conserva su ID y evita sobrescribirla.</p>
          {summary.errors.length > 0 && <details open><summary>Detalle de las filas</summary><ul>{summary.errors.map((e,i) => <li key={i}>Fila {e.row}: {e.message}</li>)}</ul></details>}</div>}
      </section>
      <section className="card" id="registro-form"><span className="step">02 / ADMINISTRAR</span><h2>{editing === null ? 'Agregar un registro' : `Editar ${editing}`}</h2>
        <form onSubmit={submit}><fieldset disabled={busy || !ready}>
          {ready && <p className="muted">{editing === null ? 'El ID del documento se genera al crear el registro. Edad y Semestre admiten números enteros; si se dejan vacíos, quedan sin valor.' : `ID del documento: ${editing}`}</p>}
          {fields.length === 0 && <p className="muted">El formulario se habilitará con los campos confirmados.</p>}
          {fields.map(f => <label key={f.name}>{f.name}{f.required ? ' *' : ''}<input type={f.type === 'INTEGER' ? 'number' : f.name === 'Correo' ? 'email' : 'text'} step={f.type === 'INTEGER' ? 1 : undefined} inputMode={['Edad','Semestre','Telefono'].includes(f.name) ? 'numeric' : undefined} required={f.required} value={form[f.name] ?? ''} onChange={e => setForm({...form,[f.name]:e.target.value})}/></label>)}
          <div className="actions"><button>{editing === null ? 'Crear registro' : 'Guardar cambios'}</button>{editing !== null && <button className="secondary" type="button" onClick={() => { setEditing(null); setForm({}); }}>Cancelar</button>}</div>
        </fieldset></form>
      </section>
    </div>
    <section className="card records"><div className="section-head"><div><span className="step">03 / CONSULTAR</span><h2>Registros guardados</h2></div><button className="secondary" disabled={busy || !ready} onClick={() => action(refresh)}>Actualizar lista</button></div>
      <form className="search" onSubmit={e => { e.preventDefault(); action(async () => { setFound(null); setFound(await request(`/registros/${urlId(query.trim())}`)); }); }}><label htmlFor="search-id">Buscar por ID del documento</label><div className="actions"><input id="search-id" placeholder="Ej.: BX2oGqeu7pVD4g7OsVVs" value={query} onChange={e => setQuery(e.target.value)} disabled={busy || !ready} required/><button disabled={busy || !ready}>Buscar</button></div></form>
      {found && <div className="search-result"><h3>Registro encontrado</h3><p className="document-id">ID: {idOf(found)}</p><dl>{fields.map(f => <div key={f.name}><dt>{f.name}</dt><dd>{String(found[f.name] ?? '—')}</dd></div>)}</dl>{rowActions(found)}<button className="secondary" disabled={busy} onClick={() => setFound(null)}>Cerrar resultado</button></div>}
      <div className="table-wrap"><table><thead><tr><th scope="col">ID del documento</th>{fields.map(f => <th key={f.name} scope="col">{f.name}</th>)}<th scope="col">Acciones</th></tr></thead><tbody>{rows.map(row => <tr key={idOf(row)}><td className="document-id">{idOf(row)}</td>{fields.map(f => <td key={f.name}>{String(row[f.name] ?? '—')}</td>)}<td>{rowActions(row)}</td></tr>)}</tbody></table></div>
      {!rows.length && <p className="empty">{connected ? 'No hay registros en esta página.' : 'Pulsa Actualizar lista para comprobar la conexión a Firestore.'}</p>}
      <nav className="pagination" aria-label="Paginación"><button className="secondary" disabled={busy || page === 0 || !ready} onClick={() => navigate(-1)}>Anterior</button><span>Página {page + 1}</span><button className="secondary" disabled={busy || !cursor || !ready} onClick={() => navigate(1)}>Siguiente</button></nav>
    </section>
    {deleting !== null && <div className="modal-backdrop"><section className="modal" role="alertdialog" aria-modal="true" aria-labelledby="delete-title"><h2 id="delete-title">Eliminar registro</h2><p>¿Confirmas que deseas eliminar el registro <strong>{deleting}</strong>? Esta acción no se puede deshacer.</p><div className="actions"><button autoFocus className="secondary" disabled={busy} onClick={() => setDeleting(null)}>Cancelar</button><button className="danger" disabled={busy} onClick={() => action(async () => { await request(`/registros/${urlId(deleting)}`, { method:'DELETE' }); if (editing === deleting) { setEditing(null); setForm({}); } setDeleting(null); setNotice('Registro eliminado.'); await refresh(); })}>Confirmar eliminación</button></div>{error && <p role="alert">{error}</p>}</section></div>}
    <footer>React + Spring Boot · Firebase se consulta exclusivamente desde el servidor.</footer>
  </main>;
}
