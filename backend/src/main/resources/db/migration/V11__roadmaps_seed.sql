-- Fase 9: roadmaps de EXEMPLO (sample = TRUE), de áreas diferentes. Conteúdo a ser revisado por
-- especialistas de cada área antes de divulgar. Os links são externos (não hospedamos conteúdo de
-- terceiros). A última etapa de cada um é PREMIUM, mas com ROADMAPS_PREMIUM_ENABLED=false (padrão)
-- tudo fica liberado. Gerado por script; para mudar conteúdo, crie uma migration nova.

DO $$
DECLARE r UUID; s UUID; m UUID; n UUID;
BEGIN
  INSERT INTO roadmaps (slug, title, description, area, language, position, access, version, sample)
  VALUES ('ciencia-da-computacao', 'Ciência da Computação', 'Da lógica de programação ao projeto final: estruturas de dados, algoritmos, bancos de dados, sistemas e prática profissional.', 'Ciência da Computação', 'pt-BR', 1, 'FREE', 1, TRUE)
  RETURNING id INTO r;

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 1, 'Fundamentos', 'Pensar como quem programa e a matemática que sustenta a computação.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Lógica de programação', 'Variáveis, condições, laços e funções: a base para escrever qualquer programa.', 'MAIN', 12) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Algoritmo (Wikipédia)', 'https://pt.wikipedia.org/wiki/Algoritmo', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Algoritmos e pseudocódigo', 'Descrever a solução passo a passo antes de escrever código.', 'SUB', FALSE, 4, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Algoritmos (Khan Academy)', 'https://pt.khanacademy.org/computing/computer-science/algorithms', 'COURSE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 2, 'Primeira linguagem: Python', 'Sintaxe, tipos e o interpretador, com exercícios curtos.', 'SUB', FALSE, 8, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Tutorial oficial do Python', 'https://docs.python.org/pt-br/3/tutorial/', 'COURSE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Matemática discreta', 'Conjuntos, lógica proposicional, indução e combinatória.', 'MAIN', 15) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Matemática discreta (Wikipédia)', 'https://pt.wikipedia.org/wiki/Matem%C3%A1tica_discreta', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 2, 'Estruturas e algoritmos', 'Organizar dados e medir o custo das soluções.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Estruturas de dados', 'Como guardar e acessar dados de forma eficiente.', 'MAIN', 20) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Estrutura de dados (Wikipédia)', 'https://pt.wikipedia.org/wiki/Estrutura_de_dados', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Listas, pilhas e filas', 'Estruturas lineares e quando usar cada uma.', 'SUB', FALSE, 6, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Pilha (Wikipédia)', 'https://pt.wikipedia.org/wiki/Pilha_(inform%C3%A1tica)', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 2, 'Árvores e grafos', 'Estruturas hierárquicas e redes de relações.', 'SUB', FALSE, 8, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Teoria dos grafos (Wikipédia)', 'https://pt.wikipedia.org/wiki/Teoria_dos_grafos', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Análise de algoritmos', 'Notação Big-O, ordenação e busca.', 'MAIN', 10) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Análise de algoritmos (Wikipédia)', 'https://pt.wikipedia.org/wiki/An%C3%A1lise_de_algoritmos', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 3, 'Sistemas', 'Onde os programas vivem: dados, sistema operacional e rede.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Banco de dados', 'Modelo relacional, normalização e transações.', 'MAIN', 15) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Banco de dados (Wikipédia)', 'https://pt.wikipedia.org/wiki/Banco_de_dados', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'SQL', 'Consultas, junções e agregações.', 'SUB', FALSE, 8, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'SQL (Wikipédia)', 'https://pt.wikipedia.org/wiki/SQL', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Sistemas operacionais', 'Processos, memória, arquivos e concorrência.', 'MAIN', 12) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Sistema operativo (Wikipédia)', 'https://pt.wikipedia.org/wiki/Sistema_operativo', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Redes de computadores', 'Modelo TCP/IP, HTTP e DNS.', 'SUB', TRUE, 8, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Rede de computadores (Wikipédia)', 'https://pt.wikipedia.org/wiki/Rede_de_computadores', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 4, 'Prática profissional', 'Trabalhar em equipe e entregar um projeto completo.', 'PREMIUM') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Engenharia de software', 'Requisitos, arquitetura, testes e manutenção.', 'MAIN', 15) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Engenharia de software (Wikipédia)', 'https://pt.wikipedia.org/wiki/Engenharia_de_software', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Controle de versão com Git', 'Commits, branches e revisão de código.', 'SUB', FALSE, 4, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Pro Git (livro oficial)', 'https://git-scm.com/book/pt-br/v2', 'BOOK');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Projeto final', 'Um projeto do começo ao fim, com documentação e apresentação.', 'MAIN', 30) RETURNING id INTO m;
END $$;

DO $$
DECLARE r UUID; s UUID; m UUID; n UUID;
BEGIN
  INSERT INTO roadmaps (slug, title, description, area, language, position, access, version, sample)
  VALUES ('direito', 'Direito', 'Uma trilha pelas bases do Direito, o Direito Público e o Privado, até a prática jurídica e a preparação para a OAB.', 'Direito', 'pt-BR', 2, 'FREE', 1, TRUE)
  RETURNING id INTO r;

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 1, 'Bases do Direito', 'Conceitos que todas as disciplinas usam.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Introdução ao Estudo do Direito', 'Norma jurídica, ordenamento e interpretação.', 'MAIN', 12) RETURNING id INTO m;
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Fontes do Direito', 'Lei, costume, jurisprudência e doutrina.', 'SUB', FALSE, 4, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Fontes do direito (Wikipédia)', 'https://pt.wikipedia.org/wiki/Fontes_do_direito', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Teoria Geral do Estado', 'Estado, soberania, poder e formas de governo.', 'MAIN', 10) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Teoria geral do Estado (Wikipédia)', 'https://pt.wikipedia.org/wiki/Teoria_geral_do_Estado', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 2, 'Direito Público', 'A organização do Estado e a relação com o cidadão.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Direito Constitucional', 'A Constituição de 1988: princípios, organização do Estado e poderes.', 'MAIN', 20) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Constituição Federal (Planalto)', 'https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm', 'OTHER');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Direitos fundamentais', 'Direitos e garantias do artigo 5º em diante.', 'SUB', FALSE, 8, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Direitos fundamentais (Wikipédia)', 'https://pt.wikipedia.org/wiki/Direitos_fundamentais', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 2, 'Controle de constitucionalidade', 'Controle difuso e concentrado.', 'SUB', FALSE, 6, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Controle de constitucionalidade (Wikipédia)', 'https://pt.wikipedia.org/wiki/Controle_de_constitucionalidade', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Direito Administrativo', 'Atos administrativos, licitações e servidores públicos.', 'MAIN', 15) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Direito administrativo (Wikipédia)', 'https://pt.wikipedia.org/wiki/Direito_administrativo', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 3, 'Direito Privado e Penal', 'Relações entre particulares e a resposta penal.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Direito Civil: parte geral', 'Pessoas, bens, fatos jurídicos e prescrição.', 'MAIN', 18) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Código Civil (Planalto)', 'https://www.planalto.gov.br/ccivil_03/leis/2002/l10406compilada.htm', 'OTHER');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Contratos', 'Formação, efeitos e extinção dos contratos.', 'SUB', FALSE, 10, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Direito Penal: parte geral', 'Teoria do crime, da pena e aplicação da lei penal.', 'MAIN', 15) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Código Penal (Planalto)', 'https://www.planalto.gov.br/ccivil_03/decreto-lei/del2848compilado.htm', 'OTHER');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Criminologia', 'O crime como fenômeno social.', 'SUB', TRUE, 6, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Criminologia (Wikipédia)', 'https://pt.wikipedia.org/wiki/Criminologia', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 4, 'Prática jurídica', 'Do processo à aprovação no Exame de Ordem.', 'PREMIUM') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Processo Civil', 'Procedimento comum, recursos e execução no CPC de 2015.', 'MAIN', 20) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Código de Processo Civil (Planalto)', 'https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13105.htm', 'OTHER');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Prática de peças processuais', 'Petição inicial, contestação e recursos.', 'SUB', FALSE, 10, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Ética profissional e Exame da OAB', 'Estatuto da Advocacia, Código de Ética e estratégia de prova.', 'MAIN', 15) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Exame de Ordem (OAB)', 'https://examedeordem.oab.org.br/', 'OTHER');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Simulados', 'Provas anteriores com tempo marcado.', 'SUB', TRUE, 10, 'RIGHT') RETURNING id INTO n;
END $$;

DO $$
DECLARE r UUID; s UUID; m UUID; n UUID;
BEGIN
  INSERT INTO roadmaps (slug, title, description, area, language, position, access, version, sample)
  VALUES ('medicina', 'Medicina', 'Do ciclo básico às bases da clínica: anatomia, fisiologia, farmacologia, semiologia e a preparação para o internato.', 'Medicina', 'pt-BR', 3, 'FREE', 1, TRUE)
  RETURNING id INTO r;

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 1, 'Ciclo básico', 'A estrutura do corpo humano.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Anatomia humana', 'Sistemas do corpo, com atlas e peças anatômicas.', 'MAIN', 30) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Anatomia humana (Wikipédia)', 'https://pt.wikipedia.org/wiki/Anatomia_humana', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Sistema musculoesquelético', 'Ossos, articulações e músculos.', 'SUB', FALSE, 10, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 2, 'Neuroanatomia', 'Sistema nervoso central e periférico.', 'SUB', FALSE, 10, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Neuroanatomia (Wikipédia)', 'https://pt.wikipedia.org/wiki/Neuroanatomia', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Histologia e embriologia', 'Tecidos do corpo e o desenvolvimento embrionário.', 'MAIN', 15) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Histologia (Wikipédia)', 'https://pt.wikipedia.org/wiki/Histologia', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 2, 'Funcionamento do corpo', 'Como os sistemas trabalham juntos.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Fisiologia', 'Funções dos órgãos e sistemas e como o corpo se regula.', 'MAIN', 25) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Fisiologia (Wikipédia)', 'https://pt.wikipedia.org/wiki/Fisiologia', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Fisiologia cardiovascular', 'Coração, pressão arterial e circulação.', 'SUB', FALSE, 8, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 2, 'Fisiologia respiratória', 'Ventilação, trocas gasosas e controle da respiração.', 'SUB', FALSE, 6, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Bioquímica', 'Metabolismo, enzimas e as moléculas da vida.', 'MAIN', 20) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Bioquímica (Wikipédia)', 'https://pt.wikipedia.org/wiki/Bioqu%C3%ADmica', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 3, 'Bases da clínica', 'Medicamentos e o encontro com o paciente.', 'FREE') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Farmacologia', 'Como os medicamentos agem e são usados com segurança.', 'MAIN', 20) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Farmacologia (Wikipédia)', 'https://pt.wikipedia.org/wiki/Farmacologia', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Farmacocinética', 'Absorção, distribuição, metabolismo e excreção.', 'SUB', FALSE, 6, 'RIGHT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Farmacocinética (Wikipédia)', 'https://pt.wikipedia.org/wiki/Farmacocin%C3%A9tica', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Semiologia', 'Sinais e sintomas: como chegar a hipóteses diagnósticas.', 'MAIN', 20) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Semiologia médica (Wikipédia)', 'https://pt.wikipedia.org/wiki/Semiologia_m%C3%A9dica', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Anamnese e exame físico', 'Entrevista clínica e exame por sistemas.', 'SUB', FALSE, 10, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Anamnese (Wikipédia)', 'https://pt.wikipedia.org/wiki/Anamnese', 'ARTICLE');

  INSERT INTO roadmap_stages (roadmap_id, position, title, description, access)
  VALUES (r, 4, 'Clínica e internato', 'Raciocínio clínico e a transição para o internato.', 'PREMIUM') RETURNING id INTO s;
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 1, 'Clínica médica', 'As doenças mais comuns do adulto, do diagnóstico ao tratamento.', 'MAIN', 30) RETURNING id INTO m;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (m, 1, 'Biblioteca Virtual em Saúde (Ministério da Saúde)', 'https://bvsms.saude.gov.br/', 'OTHER');
  INSERT INTO roadmap_nodes (stage_id, parent_id, position, title, description, kind, optional, estimated_hours, side)
  VALUES (s, m, 1, 'Medicina baseada em evidências', 'Ler e avaliar estudos clínicos.', 'SUB', TRUE, 6, 'LEFT') RETURNING id INTO n;
  INSERT INTO roadmap_node_resources (node_id, position, title, url, kind) VALUES (n, 1, 'Medicina baseada em evidências (Wikipédia)', 'https://pt.wikipedia.org/wiki/Medicina_baseada_em_evid%C3%AAncias', 'ARTICLE');
  INSERT INTO roadmap_nodes (stage_id, position, title, description, kind, estimated_hours)
  VALUES (s, 2, 'Preparação para o internato', 'Rotina hospitalar, plantões e organização dos estudos.', 'MAIN', 15) RETURNING id INTO m;
END $$;
