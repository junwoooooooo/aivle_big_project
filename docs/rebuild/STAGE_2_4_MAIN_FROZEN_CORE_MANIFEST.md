# Stage 2 / Stage 4 MAIN Frozen Core Manifest

- MAIN HEAD: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- FULL START: `8f3fc7a25e56d1b0d04e018dd89240a1f174fff5`
- Generated from the fetched refs above.
- Classification means: `BYTE_IDENTICAL` = current clean-filter blob equals MAIN; `WRAPPER_ONLY` = FULL outer integration before/after the frozen core; `NOT_ALLOWED` = may remain dormant but cannot be reached by Stage 2/4 production execution.

## Transitive production closure

Stage 2 starts at `MarketResearchInputFactory -> MarketResearchWorker -> executions.py MARKET_RESEARCH -> app.research.product_pipeline`. The synchronized MAIN tree includes `pipeline`, `product_runner`, collection adapters/blocks/services, dynamic `read_sections`, `reask_sections`, `publish_gate`, `promote_cards`, `judge_lines`, `prescribe`, `pick_lead`, `write_report`, `synthesize`, BM flow/contracts/mapping/finalization, serialization, rules and referenced assets. The tree comparison deliberately rejects extra FULL `section_recall` and `semantic_relevance` files.

Stage 4 starts at the FULL finalized-source facade, crosses the exact MAIN `MarketInterviewInputFactory`, then `MarketInterviewWorker -> executions.py -> app.interview.execute_market_interview`. Its import closure is `models/questions/targeting/runner/coding/analysis/saturation/caveats/ledger`, exact MAIN `app.twin.bank/profile/runner/task_type/caveats`, and the exact MAIN `app.providers` structured-output transport.

## BYTE_IDENTICAL files

Count: **287** (Stage 2 research tree 262; Stage 4 interview tree 10; cross-package Java/twin/provider 15).

| MAIN path | FULL current path | MAIN blob SHA | FULL blob SHA | classification |
|---|---|---|---|---|
| `ai/app/interview/__init__.py` | `ai/app/interview/__init__.py` | `9ee69c454bdb6e73dc11dc4aa4816d53ec3390f5` | `9ee69c454bdb6e73dc11dc4aa4816d53ec3390f5` | BYTE_IDENTICAL |
| `ai/app/interview/analysis.py` | `ai/app/interview/analysis.py` | `de654dd36a7de0e2bd586b656d154e9e476c2124` | `de654dd36a7de0e2bd586b656d154e9e476c2124` | BYTE_IDENTICAL |
| `ai/app/interview/caveats.py` | `ai/app/interview/caveats.py` | `39f2085baaeb149444e67f3d17debf74697b412a` | `39f2085baaeb149444e67f3d17debf74697b412a` | BYTE_IDENTICAL |
| `ai/app/interview/coding.py` | `ai/app/interview/coding.py` | `de5f2e3b55f3815dddd978ff191a8fbcb66b7a10` | `de5f2e3b55f3815dddd978ff191a8fbcb66b7a10` | BYTE_IDENTICAL |
| `ai/app/interview/ledger.py` | `ai/app/interview/ledger.py` | `b07df84f94bfbbcbbde3b88f8a0bcfb9816c80ff` | `b07df84f94bfbbcbbde3b88f8a0bcfb9816c80ff` | BYTE_IDENTICAL |
| `ai/app/interview/models.py` | `ai/app/interview/models.py` | `0a0439b98998d9b9ab5336c21da88d1b0964fac4` | `0a0439b98998d9b9ab5336c21da88d1b0964fac4` | BYTE_IDENTICAL |
| `ai/app/interview/questions.py` | `ai/app/interview/questions.py` | `736d15fb2443c9ca4210583fa114dcec255ab2b6` | `736d15fb2443c9ca4210583fa114dcec255ab2b6` | BYTE_IDENTICAL |
| `ai/app/interview/runner.py` | `ai/app/interview/runner.py` | `228f5e271bf4b41f15007199d9426333248ccf12` | `228f5e271bf4b41f15007199d9426333248ccf12` | BYTE_IDENTICAL |
| `ai/app/interview/saturation.py` | `ai/app/interview/saturation.py` | `3c763bef7171ea53f855fff7d575a8aea8dbac37` | `3c763bef7171ea53f855fff7d575a8aea8dbac37` | BYTE_IDENTICAL |
| `ai/app/interview/targeting.py` | `ai/app/interview/targeting.py` | `a67e2309c18fb006da387277bf28601e55b1fbf4` | `a67e2309c18fb006da387277bf28601e55b1fbf4` | BYTE_IDENTICAL |
| `ai/app/research/__init__.py` | `ai/app/research/__init__.py` | `8b137891791fe96927ad78e64b0aad7bded08bdc` | `8b137891791fe96927ad78e64b0aad7bded08bdc` | BYTE_IDENTICAL |
| `ai/app/research/bm/__init__.py` | `ai/app/research/bm/__init__.py` | `f17284667fa180deb74f3247e6e942176a5145a9` | `f17284667fa180deb74f3247e6e942176a5145a9` | BYTE_IDENTICAL |
| `ai/app/research/bm/analyze.py` | `ai/app/research/bm/analyze.py` | `fdb447be79f99bec0a53008ec76e2914d743c0f0` | `fdb447be79f99bec0a53008ec76e2914d743c0f0` | BYTE_IDENTICAL |
| `ai/app/research/bm/contracts.py` | `ai/app/research/bm/contracts.py` | `b83cb3e11d11c187f07cfe95f8904deafa605712` | `b83cb3e11d11c187f07cfe95f8904deafa605712` | BYTE_IDENTICAL |
| `ai/app/research/bm/diagnostics.py` | `ai/app/research/bm/diagnostics.py` | `dca92da6f406afacbb75fafbdebbe5292606a221` | `dca92da6f406afacbb75fafbdebbe5292606a221` | BYTE_IDENTICAL |
| `ai/app/research/bm/finalize.py` | `ai/app/research/bm/finalize.py` | `fd9ac6d37390459ec8a820f7f99f587f58e41a74` | `fd9ac6d37390459ec8a820f7f99f587f58e41a74` | BYTE_IDENTICAL |
| `ai/app/research/bm/flow.py` | `ai/app/research/bm/flow.py` | `0d1ed6a8f69f965aec4ec29c2af7578674606106` | `0d1ed6a8f69f965aec4ec29c2af7578674606106` | BYTE_IDENTICAL |
| `ai/app/research/bm/handoff.py` | `ai/app/research/bm/handoff.py` | `4bd95fae50a57870ae4d8e83523047c44436804b` | `4bd95fae50a57870ae4d8e83523047c44436804b` | BYTE_IDENTICAL |
| `ai/app/research/bm/normalize.py` | `ai/app/research/bm/normalize.py` | `9da0ce9b97ebfa8cb7b54b90c15863aac35b98cd` | `9da0ce9b97ebfa8cb7b54b90c15863aac35b98cd` | BYTE_IDENTICAL |
| `ai/app/research/bm/prompt.py` | `ai/app/research/bm/prompt.py` | `a4cd9aafa35fc411634ff73d22ed43ff3de4010e` | `a4cd9aafa35fc411634ff73d22ed43ff3de4010e` | BYTE_IDENTICAL |
| `ai/app/research/market_ledger_artifact.py` | `ai/app/research/market_ledger_artifact.py` | `c560cd052a98d5801e702f78aa813e1131a7451c` | `c560cd052a98d5801e702f78aa813e1131a7451c` | BYTE_IDENTICAL |
| `ai/app/research/pipeline.py` | `ai/app/research/pipeline.py` | `02dbe0107687e451e6810eff1382b0738d3154a8` | `02dbe0107687e451e6810eff1382b0738d3154a8` | BYTE_IDENTICAL |
| `ai/app/research/product_market_join.py` | `ai/app/research/product_market_join.py` | `e6c5d07d5d21f873f4a8a99ebf855aa865470972` | `e6c5d07d5d21f873f4a8a99ebf855aa865470972` | BYTE_IDENTICAL |
| `ai/app/research/product_pipeline.py` | `ai/app/research/product_pipeline.py` | `b0bbce17726135448096487c33b846ca18236922` | `b0bbce17726135448096487c33b846ca18236922` | BYTE_IDENTICAL |
| `ai/app/research/product_runner.py` | `ai/app/research/product_runner.py` | `cb3525de7fb21920c1e7732aaee5d727c69f159a` | `cb3525de7fb21920c1e7732aaee5d727c69f159a` | BYTE_IDENTICAL |
| `ai/app/research/progress_jsonl.py` | `ai/app/research/progress_jsonl.py` | `544e5799c03a1535b4bac232e12142bce02e4583` | `544e5799c03a1535b4bac232e12142bce02e4583` | BYTE_IDENTICAL |
| `ai/app/research/research2/adapters/_cache_corpcode.json` | `ai/app/research/research2/adapters/_cache_corpcode.json` | `aefd84b31aaf474635ef1d1ff28cd9f90c5d16b1` | `aefd84b31aaf474635ef1d1ff28cd9f90c5d16b1` | BYTE_IDENTICAL |
| `ai/app/research/research2/adapters/base.py` | `ai/app/research/research2/adapters/base.py` | `c3b2131a9c00dc7623fa8c0267c8aa9510c64d12` | `c3b2131a9c00dc7623fa8c0267c8aa9510c64d12` | BYTE_IDENTICAL |
| `ai/app/research/research2/adapters/dart.py` | `ai/app/research/research2/adapters/dart.py` | `5d40f4905464fafcb1d1b82eea49e6d3fef8a3bb` | `5d40f4905464fafcb1d1b82eea49e6d3fef8a3bb` | BYTE_IDENTICAL |
| `ai/app/research/research2/adapters/doc_window.py` | `ai/app/research/research2/adapters/doc_window.py` | `8420e22b76ec7d0009ba723cd6dc757b24e97cd7` | `8420e22b76ec7d0009ba723cd6dc757b24e97cd7` | BYTE_IDENTICAL |
| `ai/app/research/research2/adapters/kosis.py` | `ai/app/research/research2/adapters/kosis.py` | `894e10851c1b182840840cbe8f44ff728d2bae09` | `894e10851c1b182840840cbe8f44ff728d2bae09` | BYTE_IDENTICAL |
| `ai/app/research/research2/adapters/web.py` | `ai/app/research/research2/adapters/web.py` | `bfc60754350997a5478787a4ba6474f84c949ba8` | `bfc60754350997a5478787a4ba6474f84c949ba8` | BYTE_IDENTICAL |
| `ai/app/research/research2/blocks/a_design.py` | `ai/app/research/research2/blocks/a_design.py` | `b119ccd75b59c434a739792378b4788075b24b8e` | `b119ccd75b59c434a739792378b4788075b24b8e` | BYTE_IDENTICAL |
| `ai/app/research/research2/blocks/a_desk.py` | `ai/app/research/research2/blocks/a_desk.py` | `a5ec94a07fcefe99ea3c442fbaf019f4b06b98c3` | `a5ec94a07fcefe99ea3c442fbaf019f4b06b98c3` | BYTE_IDENTICAL |
| `ai/app/research/research2/blocks/b_estimate.py` | `ai/app/research/research2/blocks/b_estimate.py` | `b4908fbe803f152eaca1361854134e9d31c85eec` | `b4908fbe803f152eaca1361854134e9d31c85eec` | BYTE_IDENTICAL |
| `ai/app/research/research2/blocks/c_chain.py` | `ai/app/research/research2/blocks/c_chain.py` | `2b03fe6d7116c2112c21ff4f8304129928f34977` | `2b03fe6d7116c2112c21ff4f8304129928f34977` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_beauty-noshow.json` | `ai/app/research/research2/data/concept_beauty-noshow.json` | `65167ec37cce2a2843de43ed1294e280a46ed2f6` | `65167ec37cce2a2843de43ed1294e280a46ed2f6` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_edge-adcommunity.json` | `ai/app/research/research2/data/concept_edge-adcommunity.json` | `c20965eb48b77b66fd8a770cd0dbba93c483fed9` | `c20965eb48b77b66fd8a770cd0dbba93c483fed9` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_edge-delivery.json` | `ai/app/research/research2/data/concept_edge-delivery.json` | `789e76aef2b42dd60c5ab07211ce48996ae6f82f` | `789e76aef2b42dd60c5ab07211ce48996ae6f82f` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_edge-govbot.json` | `ai/app/research/research2/data/concept_edge-govbot.json` | `dec6769f58ba84ea1983ff14bd775e94ba265d46` | `dec6769f58ba84ea1983ff14bd775e94ba265d46` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_hmr-product.json` | `ai/app/research/research2/data/concept_hmr-product.json` | `e20505062b54168abd4c0bbba7d4d182a868eea8` | `e20505062b54168abd4c0bbba7d4d182a868eea8` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_hmr-solo.json` | `ai/app/research/research2/data/concept_hmr-solo.json` | `92c522278868f1df2305da003f598a5e58e78faa` | `92c522278868f1df2305da003f598a5e58e78faa` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_household-ledger.json` | `ai/app/research/research2/data/concept_household-ledger.json` | `6ccfc507602b5b16e27ae84e3f375cace9471b9d` | `6ccfc507602b5b16e27ae84e3f375cace9471b9d` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_kbeauty-sea.json` | `ai/app/research/research2/data/concept_kbeauty-sea.json` | `a2979afa3b7c44adc7664d89a336d3ca010cf22f` | `a2979afa3b7c44adc7664d89a336d3ca010cf22f` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_nailrobot-rental.json` | `ai/app/research/research2/data/concept_nailrobot-rental.json` | `9e83da79aca0b8f0a6e4adcf5a4bef427f8c337d` | `9e83da79aca0b8f0a6e4adcf5a4bef427f8c337d` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_pet-treat.json` | `ai/app/research/research2/data/concept_pet-treat.json` | `d1b2c4cc9dcbe7a08bbd4150f758d2345ee92ce6` | `d1b2c4cc9dcbe7a08bbd4150f758d2345ee92ce6` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept_pilates-member.json` | `ai/app/research/research2/data/concept_pilates-member.json` | `3bbfff60ab216df4b476761aa58a2bdf92336a67` | `3bbfff60ab216df4b476761aa58a2bdf92336a67` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/concept.json` | `ai/app/research/research2/data/concept.json` | `354e755c12d79424491d23dac1cb50375bd2626a` | `354e755c12d79424491d23dac1cb50375bd2626a` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_beauty-price.json` | `ai/app/research/research2/data/direct_urls_beauty-price.json` | `118f5eafe1d1c0366214b2f94aaa50599ffeac6e` | `118f5eafe1d1c0366214b2f94aaa50599ffeac6e` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_mss-noshow-refetch.json` | `ai/app/research/research2/data/direct_urls_mss-noshow-refetch.json` | `d241b08c96008b16eb405bcedf4030a7140af7ba` | `d241b08c96008b16eb405bcedf4030a7140af7ba` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_mss-noshow.json` | `ai/app/research/research2/data/direct_urls_mss-noshow.json` | `b37acd3b411d25574425e197aeb0408936845850` | `b37acd3b411d25574425e197aeb0408936845850` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_pain-pdf.json` | `ai/app/research/research2/data/direct_urls_pain-pdf.json` | `ab0289357dde5202d49c539249be033dc5dd0348` | `ab0289357dde5202d49c539249be033dc5dd0348` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_pet-price-s13.json` | `ai/app/research/research2/data/direct_urls_pet-price-s13.json` | `5ff52a70b23e5ce6ea0af0092b50a834cee6d608` | `5ff52a70b23e5ce6ea0af0092b50a834cee6d608` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_pet-price.json` | `ai/app/research/research2/data/direct_urls_pet-price.json` | `f35eee9af6e5420344fe21dd2bfa0dad58834ef8` | `f35eee9af6e5420344fe21dd2bfa0dad58834ef8` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_tavily-01.json` | `ai/app/research/research2/data/direct_urls_tavily-01.json` | `95a03f157faf1b92dfafdd764b7f982358157865` | `95a03f157faf1b92dfafdd764b7f982358157865` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls_userdocs-pain.json` | `ai/app/research/research2/data/direct_urls_userdocs-pain.json` | `cfe75ba2d376692c3ad876f6c6ec81b8eaf04c13` | `cfe75ba2d376692c3ad876f6c6ec81b8eaf04c13` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/direct_urls.json` | `ai/app/research/research2/data/direct_urls.json` | `ccd91ab4f2938f5c3faf9cb48c0ae320e2507bb6` | `ccd91ab4f2938f5c3faf9cb48c0ae320e2507bb6` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_beauty-noshow.json` | `ai/app/research/research2/data/formulas_beauty-noshow.json` | `5ebffbd74dd0d8e5cb6de618520ea49b9225ab3b` | `5ebffbd74dd0d8e5cb6de618520ea49b9225ab3b` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_beauty-p2check-g61.json` | `ai/app/research/research2/data/formulas_beauty-p2check-g61.json` | `893c804983cb356373f25b626ea49d5496eada57` | `893c804983cb356373f25b626ea49d5496eada57` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_beauty-p2check.json` | `ai/app/research/research2/data/formulas_beauty-p2check.json` | `6babaefbc3e2cc40dd8c09197b08a4c24d5d2a30` | `6babaefbc3e2cc40dd8c09197b08a4c24d5d2a30` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_hmr-01.json` | `ai/app/research/research2/data/formulas_hmr-01.json` | `e0625a415774c1c758b8da95bc07baef3d11a91a` | `e0625a415774c1c758b8da95bc07baef3d11a91a` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p12-gate3.json` | `ai/app/research/research2/data/formulas_p12-gate3.json` | `379c4bc77274b31f977df70c7723f294b891ff1e` | `379c4bc77274b31f977df70c7723f294b891ff1e` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p20-d.json` | `ai/app/research/research2/data/formulas_p20-d.json` | `1a7da4e0240636969db62d7d9f1b128c97b40096` | `1a7da4e0240636969db62d7d9f1b128c97b40096` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p22-c.json` | `ai/app/research/research2/data/formulas_p22-c.json` | `c20e289ab4452f2c995fabc9b7e3bd384d8774bc` | `c20e289ab4452f2c995fabc9b7e3bd384d8774bc` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p23-c.json` | `ai/app/research/research2/data/formulas_p23-c.json` | `4775d342e39bb909cd768e547a29e40df4f40df3` | `4775d342e39bb909cd768e547a29e40df4f40df3` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p23-household-ledger.json` | `ai/app/research/research2/data/formulas_p23-household-ledger.json` | `6080e74420cfb0a4f5bb33831b7e628fc3a0db14` | `6080e74420cfb0a4f5bb33831b7e628fc3a0db14` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p24-b2.json` | `ai/app/research/research2/data/formulas_p24-b2.json` | `d342709cf631261319ee21f0cf9bd9b191d0b29f` | `d342709cf631261319ee21f0cf9bd9b191d0b29f` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p25-e.json` | `ai/app/research/research2/data/formulas_p25-e.json` | `919eb6fcdd38d9d61284da72aafb449398ea75cd` | `919eb6fcdd38d9d61284da72aafb449398ea75cd` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p26-d.json` | `ai/app/research/research2/data/formulas_p26-d.json` | `279b18875f7ea6a74d739a73405a387ad1bfc050` | `279b18875f7ea6a74d739a73405a387ad1bfc050` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p27-d.json` | `ai/app/research/research2/data/formulas_p27-d.json` | `565a3c6e122d83febd2136cd71d0263c595eefbe` | `565a3c6e122d83febd2136cd71d0263c595eefbe` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p32-auto01.json` | `ai/app/research/research2/data/formulas_p32-auto01.json` | `56bfc49a282f3ffc6ecd0d4154879b8873ecfe6d` | `56bfc49a282f3ffc6ecd0d4154879b8873ecfe6d` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p33-auto.json` | `ai/app/research/research2/data/formulas_p33-auto.json` | `aa568b62724ccfcba0a73fbbcbe137ca6206f603` | `aa568b62724ccfcba0a73fbbcbe137ca6206f603` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p33-var-1.json` | `ai/app/research/research2/data/formulas_p33-var-1.json` | `b756ff87f775080ec0f20c6b167c34f087ee8fc4` | `b756ff87f775080ec0f20c6b167c34f087ee8fc4` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p33-var-2.json` | `ai/app/research/research2/data/formulas_p33-var-2.json` | `3624b2cc39b657d13de6043aa16f166a3aaaacf9` | `3624b2cc39b657d13de6043aa16f166a3aaaacf9` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_p43-smoke-01.json` | `ai/app/research/research2/data/formulas_p43-smoke-01.json` | `6b8700634f1bba5ebf1562054f974497659492f1` | `6b8700634f1bba5ebf1562054f974497659492f1` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_paid31-beauty.json` | `ai/app/research/research2/data/formulas_paid31-beauty.json` | `512d9c4ae8b1041e0e550ec11c3e41779fd6b383` | `512d9c4ae8b1041e0e550ec11c3e41779fd6b383` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_paid31a-hmr.json` | `ai/app/research/research2/data/formulas_paid31a-hmr.json` | `5889ca04fb672b46e5aecc8982f77392101dd0c0` | `5889ca04fb672b46e5aecc8982f77392101dd0c0` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_paid31b-hmr.json` | `ai/app/research/research2/data/formulas_paid31b-hmr.json` | `e5f786a478e386cbc0001c401779811c7af36a2b` | `e5f786a478e386cbc0001c401779811c7af36a2b` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_pre-hmr2.json` | `ai/app/research/research2/data/formulas_pre-hmr2.json` | `71ee19f49a01f5c4120b893efd2a3ab9140064a1` | `71ee19f49a01f5c4120b893efd2a3ab9140064a1` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_pre-hmr3.json` | `ai/app/research/research2/data/formulas_pre-hmr3.json` | `d05adf0f7fb0422216358f932b51b2a16060cc75` | `d05adf0f7fb0422216358f932b51b2a16060cc75` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_smoke-collect-01.json` | `ai/app/research/research2/data/formulas_smoke-collect-01.json` | `1c4ae521960627ee1247eb2d495ba7b0beac769d` | `1c4ae521960627ee1247eb2d495ba7b0beac769d` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_var-v1.json` | `ai/app/research/research2/data/formulas_var-v1.json` | `c633afbd60a338036f040acc70c6dc5779babdb1` | `c633afbd60a338036f040acc70c6dc5779babdb1` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas_var-v2.json` | `ai/app/research/research2/data/formulas_var-v2.json` | `7abeef49590c09e0c0645a7c86d4cd44005f8627` | `7abeef49590c09e0c0645a7c86d4cd44005f8627` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/formulas.json` | `ai/app/research/research2/data/formulas.json` | `84189db1ccf5dbbf2aed017b3d9e6a8e8e7065f4` | `84189db1ccf5dbbf2aed017b3d9e6a8e8e7065f4` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/garbage_sample.json` | `ai/app/research/research2/data/garbage_sample.json` | `c1b45d69d150e5a689b656ef7d4e8fad36f8cb2e` | `c1b45d69d150e5a689b656ef7d4e8fad36f8cb2e` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/golden.json` | `ai/app/research/research2/data/golden.json` | `1995ba42459d1425c3ab87b1e198e2684ff84b81` | `1995ba42459d1425c3ab87b1e198e2684ff84b81` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/recall_probes.json` | `ai/app/research/research2/data/recall_probes.json` | `92fb950acfceeb75bf51677186eebe7e97669dd5` | `92fb950acfceeb75bf51677186eebe7e97669dd5` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/reference_facts.json` | `ai/app/research/research2/data/reference_facts.json` | `0f597643056422068537627e9dd379e1d3ab187d` | `0f597643056422068537627e9dd379e1d3ab187d` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_beauty-noshow.json` | `ai/app/research/research2/data/slots_beauty-noshow.json` | `e0fd545d46db2e50957fbf7325027b428460ef9a` | `e0fd545d46db2e50957fbf7325027b428460ef9a` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_beauty-p2check-g61.json` | `ai/app/research/research2/data/slots_beauty-p2check-g61.json` | `1993a5c3b010532eed65c491fd3d307d3091f304` | `1993a5c3b010532eed65c491fd3d307d3091f304` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_beauty-p2check.json` | `ai/app/research/research2/data/slots_beauty-p2check.json` | `be53bfc6698f022bf009b3519fbbb9afaed8df94` | `be53bfc6698f022bf009b3519fbbb9afaed8df94` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-01.json` | `ai/app/research/research2/data/slots_hmr-01.json` | `977ae98a0a6433a3469d4467798f3e55aa8b528a` | `977ae98a0a6433a3469d4467798f3e55aa8b528a` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin02.json` | `ai/app/research/research2/data/slots_hmr-pin02.json` | `8ea2eb188b1c02862983c11457f910b30a3d8b00` | `8ea2eb188b1c02862983c11457f910b30a3d8b00` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin03.json` | `ai/app/research/research2/data/slots_hmr-pin03.json` | `5dba3099b0f9a8659d8f14a03a9ef675467d75ec` | `5dba3099b0f9a8659d8f14a03a9ef675467d75ec` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin04.json` | `ai/app/research/research2/data/slots_hmr-pin04.json` | `434d082e0b6c35b9dc566ee1962754d06c87314f` | `434d082e0b6c35b9dc566ee1962754d06c87314f` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin05.json` | `ai/app/research/research2/data/slots_hmr-pin05.json` | `88be1266f651a0c9751840cfdf9ab2dcbf7e0aab` | `88be1266f651a0c9751840cfdf9ab2dcbf7e0aab` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin06.json` | `ai/app/research/research2/data/slots_hmr-pin06.json` | `1029152740136c0f287a0b1a502a707d15f32fb4` | `1029152740136c0f287a0b1a502a707d15f32fb4` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin07.json` | `ai/app/research/research2/data/slots_hmr-pin07.json` | `67449802d72bef2a665d42127e0de558ec12f54f` | `67449802d72bef2a665d42127e0de558ec12f54f` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin08.json` | `ai/app/research/research2/data/slots_hmr-pin08.json` | `185f62c5a1c626a43cf8bc01494b8d52c934ab0e` | `185f62c5a1c626a43cf8bc01494b8d52c934ab0e` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_hmr-pin09.json` | `ai/app/research/research2/data/slots_hmr-pin09.json` | `8adebb9712a76b6e2d5acb89ae38528fbd2bc721` | `8adebb9712a76b6e2d5acb89ae38528fbd2bc721` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p12-gate3.json` | `ai/app/research/research2/data/slots_p12-gate3.json` | `9d4dc53987d234acde3c034b2b3559f89c2f7ebf` | `9d4dc53987d234acde3c034b2b3559f89c2f7ebf` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p20-d.json` | `ai/app/research/research2/data/slots_p20-d.json` | `b7ccc9950d75e1c6655688d19f8f09bdebe1acd8` | `b7ccc9950d75e1c6655688d19f8f09bdebe1acd8` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p22-c.json` | `ai/app/research/research2/data/slots_p22-c.json` | `23915317fa6d1c09257633159aaac46b21d535b2` | `23915317fa6d1c09257633159aaac46b21d535b2` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p23-c.json` | `ai/app/research/research2/data/slots_p23-c.json` | `77de2a4ce1c3c6143756f328ab285f6e141caaad` | `77de2a4ce1c3c6143756f328ab285f6e141caaad` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p23-household-ledger.json` | `ai/app/research/research2/data/slots_p23-household-ledger.json` | `313d391e164a741980157906a4ad296f88155fae` | `313d391e164a741980157906a4ad296f88155fae` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p24-b2.json` | `ai/app/research/research2/data/slots_p24-b2.json` | `ee257c2a89562619fa09fcdea1c3c3e2dc8d2bd1` | `ee257c2a89562619fa09fcdea1c3c3e2dc8d2bd1` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p25-e.json` | `ai/app/research/research2/data/slots_p25-e.json` | `3b9f392a2452bc74f6375e49b46955217ec7ecdf` | `3b9f392a2452bc74f6375e49b46955217ec7ecdf` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p26-d.json` | `ai/app/research/research2/data/slots_p26-d.json` | `abf64580fed793066acafae45a0f7eb4112677ca` | `abf64580fed793066acafae45a0f7eb4112677ca` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p27-d.json` | `ai/app/research/research2/data/slots_p27-d.json` | `2ab00402ae9f99e0f942fab7b282ff230416987c` | `2ab00402ae9f99e0f942fab7b282ff230416987c` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p28-b.json` | `ai/app/research/research2/data/slots_p28-b.json` | `a38d33a63aa2e7cf4c8e5465fb7cb5f80548cda7` | `a38d33a63aa2e7cf4c8e5465fb7cb5f80548cda7` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p29-b.json` | `ai/app/research/research2/data/slots_p29-b.json` | `1480d3a510b27bee502b61f6070990ed0095f002` | `1480d3a510b27bee502b61f6070990ed0095f002` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p30-c.json` | `ai/app/research/research2/data/slots_p30-c.json` | `f649e8d69668d9eb6e891e4c20ab96686b0b200f` | `f649e8d69668d9eb6e891e4c20ab96686b0b200f` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p32-auto01.json` | `ai/app/research/research2/data/slots_p32-auto01.json` | `01469368a05e655b04ea0a7b3c43570a7bd660b1` | `01469368a05e655b04ea0a7b3c43570a7bd660b1` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p33-auto-repaired.json` | `ai/app/research/research2/data/slots_p33-auto-repaired.json` | `993b44279acfadc2d99c9610346dec67a7a84203` | `993b44279acfadc2d99c9610346dec67a7a84203` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p33-auto.json` | `ai/app/research/research2/data/slots_p33-auto.json` | `21c0bdf601e407722a8cf1132ba1d8e08a39d6bc` | `21c0bdf601e407722a8cf1132ba1d8e08a39d6bc` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p33-var-1.json` | `ai/app/research/research2/data/slots_p33-var-1.json` | `99e6e77868fa00e14c7438e96b84efc14eef2f68` | `99e6e77868fa00e14c7438e96b84efc14eef2f68` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p33-var-2.json` | `ai/app/research/research2/data/slots_p33-var-2.json` | `7a4334475cb70173faef28dba19d89cbfe74c91a` | `7a4334475cb70173faef28dba19d89cbfe74c91a` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_p43-smoke-01.json` | `ai/app/research/research2/data/slots_p43-smoke-01.json` | `42770d5879b00e0ed1b535b799058c24c1778f38` | `42770d5879b00e0ed1b535b799058c24c1778f38` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_paid31-beauty.json` | `ai/app/research/research2/data/slots_paid31-beauty.json` | `fd1f6ee5794f8dee380e22ecaca2e7a5ff1a0d91` | `fd1f6ee5794f8dee380e22ecaca2e7a5ff1a0d91` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_paid31a-hmr.json` | `ai/app/research/research2/data/slots_paid31a-hmr.json` | `bc3587219f09fe9170869d4110cece57df7c6e2c` | `bc3587219f09fe9170869d4110cece57df7c6e2c` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_paid31b-hmr.json` | `ai/app/research/research2/data/slots_paid31b-hmr.json` | `2cb6ad5535b55094e0f20e6d145cd9f9e290758c` | `2cb6ad5535b55094e0f20e6d145cd9f9e290758c` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_pre-hmr2.json` | `ai/app/research/research2/data/slots_pre-hmr2.json` | `a455bf75ba4a9960a4b98e395d6631d8cd6586a4` | `a455bf75ba4a9960a4b98e395d6631d8cd6586a4` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_pre-hmr3.json` | `ai/app/research/research2/data/slots_pre-hmr3.json` | `806b6cbedbe78393095ec5a6e9d7122fbd481936` | `806b6cbedbe78393095ec5a6e9d7122fbd481936` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_r4-snapshot.json` | `ai/app/research/research2/data/slots_r4-snapshot.json` | `b50e388d2fdd09a692341f9a4031a029672c9569` | `b50e388d2fdd09a692341f9a4031a029672c9569` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_run-hmr-product.json` | `ai/app/research/research2/data/slots_run-hmr-product.json` | `228fd76fcfa6b47fbe0a8fe5385b48bd065134f2` | `228fd76fcfa6b47fbe0a8fe5385b48bd065134f2` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_smoke-collect-01.json` | `ai/app/research/research2/data/slots_smoke-collect-01.json` | `5d8722dc8e3827347676db1633891da128a06d92` | `5d8722dc8e3827347676db1633891da128a06d92` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_var-v1.json` | `ai/app/research/research2/data/slots_var-v1.json` | `c2773fc50a10a6026321e1c3ccdf308f7a6ead85` | `c2773fc50a10a6026321e1c3ccdf308f7a6ead85` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots_var-v2.json` | `ai/app/research/research2/data/slots_var-v2.json` | `80eb669f2defa98036fca2d9f04d510af4bbd98a` | `80eb669f2defa98036fca2d9f04d510af4bbd98a` | BYTE_IDENTICAL |
| `ai/app/research/research2/data/slots.json` | `ai/app/research/research2/data/slots.json` | `adedf28eff09b65d8307a01fcfa537de4a0dcea5` | `adedf28eff09b65d8307a01fcfa537de4a0dcea5` | BYTE_IDENTICAL |
| `ai/app/research/research2/eval.py` | `ai/app/research/research2/eval.py` | `64770df81d8acc12f652772bda0cf72ef594f011` | `64770df81d8acc12f652772bda0cf72ef594f011` | BYTE_IDENTICAL |
| `ai/app/research/research2/expected.md` | `ai/app/research/research2/expected.md` | `2318a849ba4ce833fbe45391d053c59497c10978` | `2318a849ba4ce833fbe45391d053c59497c10978` | BYTE_IDENTICAL |
| `ai/app/research/research2/fillaxis.py` | `ai/app/research/research2/fillaxis.py` | `93e7554bdfcf392900ce14b8703392f858b4ef18` | `93e7554bdfcf392900ce14b8703392f858b4ef18` | BYTE_IDENTICAL |
| `ai/app/research/research2/harness/doc_intake.py` | `ai/app/research/research2/harness/doc_intake.py` | `2870a433a71cc3629381a4f18e679bdb26be49fb` | `2870a433a71cc3629381a4f18e679bdb26be49fb` | BYTE_IDENTICAL |
| `ai/app/research/research2/harness/gate.py` | `ai/app/research/research2/harness/gate.py` | `50f8b751a02bb9e3119fe0b2d975348df70f1e70` | `50f8b751a02bb9e3119fe0b2d975348df70f1e70` | BYTE_IDENTICAL |
| `ai/app/research/research2/harness/intake/mss_noshow.json` | `ai/app/research/research2/harness/intake/mss_noshow.json` | `7831ef281201952c77a39c1ef395e4cca81c56d8` | `7831ef281201952c77a39c1ef395e4cca81c56d8` | BYTE_IDENTICAL |
| `ai/app/research/research2/harness/intake/pain_beauty.json` | `ai/app/research/research2/harness/intake/pain_beauty.json` | `e8a6170546bc9c814b03fd5c191ea498dc70091b` | `e8a6170546bc9c814b03fd5c191ea498dc70091b` | BYTE_IDENTICAL |
| `ai/app/research/research2/harness/slot_harness.py` | `ai/app/research/research2/harness/slot_harness.py` | `363378e48193447d5fb4ec31cb62b8d389670dcd` | `363378e48193447d5fb4ec31cb62b8d389670dcd` | BYTE_IDENTICAL |
| `ai/app/research/research2/harness/tavily_intake.py` | `ai/app/research/research2/harness/tavily_intake.py` | `444a64296c7cc32c41497879f12addc061189bf3` | `444a64296c7cc32c41497879f12addc061189bf3` | BYTE_IDENTICAL |
| `ai/app/research/research2/harness/vocab.json` | `ai/app/research/research2/harness/vocab.json` | `7429fe6896c99dd660bc2e4230ff17ae05d322e8` | `7429fe6896c99dd660bc2e4230ff17ae05d322e8` | BYTE_IDENTICAL |
| `ai/app/research/research2/pdf_text.py` | `ai/app/research/research2/pdf_text.py` | `2ae3598b9412f2370c8c6fa2a64c2aca577bf1b9` | `2ae3598b9412f2370c8c6fa2a64c2aca577bf1b9` | BYTE_IDENTICAL |
| `ai/app/research/research2/prompts.py` | `ai/app/research/research2/prompts.py` | `ac85d9fb48be81878c1b5f2f6413bc9358c26763` | `ac85d9fb48be81878c1b5f2f6413bc9358c26763` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/adapters.v1.json` | `ai/app/research/research2/rules/adapters.v1.json` | `00b4315b07780aa90e28b118a8225a91add1941e` | `00b4315b07780aa90e28b118a8225a91add1941e` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/assumptions.v1.json` | `ai/app/research/research2/rules/assumptions.v1.json` | `46602f03a3b0ec15a578dd112f43a6404eba81c8` | `46602f03a3b0ec15a578dd112f43a6404eba81c8` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/bm_gate.v1.json` | `ai/app/research/research2/rules/bm_gate.v1.json` | `504b9d036e0abf08828ec6ad169274ea87bdeb54` | `504b9d036e0abf08828ec6ad169274ea87bdeb54` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/channel_assumption.v1.json` | `ai/app/research/research2/rules/channel_assumption.v1.json` | `ece49454d7f9a56cceceb800ccc42c3202db3eb2` | `ece49454d7f9a56cceceb800ccc42c3202db3eb2` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/consistency.v1.json` | `ai/app/research/research2/rules/consistency.v1.json` | `f9348ac6c0df7c4c7ffbc66771ddc8214aed30e8` | `f9348ac6c0df7c4c7ffbc66771ddc8214aed30e8` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/failopen.v1.json` | `ai/app/research/research2/rules/failopen.v1.json` | `eec39fe0fc7918a63772b07c417dd892c5b252e5` | `eec39fe0fc7918a63772b07c417dd892c5b252e5` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/fill.v2.json` | `ai/app/research/research2/rules/fill.v2.json` | `f77d1f8455c973db015274b895f1066892a6b69d` | `f77d1f8455c973db015274b895f1066892a6b69d` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/growth.v1.json` | `ai/app/research/research2/rules/growth.v1.json` | `ceed25b14d6adeaf9bfb39691dc44f7bf80c38e9` | `ceed25b14d6adeaf9bfb39691dc44f7bf80c38e9` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/guards.v1.json` | `ai/app/research/research2/rules/guards.v1.json` | `4392b22e6d911d187a332cea9c82a3a7626ed0ac` | `4392b22e6d911d187a332cea9c82a3a7626ed0ac` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/prescribe.v1.json` | `ai/app/research/research2/rules/prescribe.v1.json` | `a09f695630942e8d8a7d6601ed2700e0608a7b23` | `a09f695630942e8d8a7d6601ed2700e0608a7b23` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/promote.v1.json` | `ai/app/research/research2/rules/promote.v1.json` | `0e11e690735a5c2155694ca18f348017a7d7b87a` | `0e11e690735a5c2155694ca18f348017a7d7b87a` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/publish.v1.json` | `ai/app/research/research2/rules/publish.v1.json` | `8318180a73d133da3a3cb1d354aad31d0c07a111` | `8318180a73d133da3a3cb1d354aad31d0c07a111` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/rule_pins.json` | `ai/app/research/research2/rules/rule_pins.json` | `f12875f865deafa8779c757ceec40f3c45568805` | `f12875f865deafa8779c757ceec40f3c45568805` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/scoring.v1.json` | `ai/app/research/research2/rules/scoring.v1.json` | `a0b5083c4347fb7b706db839dde9ac9385b11acc` | `a0b5083c4347fb7b706db839dde9ac9385b11acc` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/series_unit.v1.json` | `ai/app/research/research2/rules/series_unit.v1.json` | `4e72bdfcba7dce7d2e58d45e58f89cb9a2b16517` | `4e72bdfcba7dce7d2e58d45e58f89cb9a2b16517` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/slotcheck.v1.json` | `ai/app/research/research2/rules/slotcheck.v1.json` | `e4ea16f952550e1dcbb1ca6806f1f5493904d81b` | `e4ea16f952550e1dcbb1ca6806f1f5493904d81b` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/source_signature.v1.json` | `ai/app/research/research2/rules/source_signature.v1.json` | `2bb7bae2d54597f651a83f25c6b68e557aba5961` | `2bb7bae2d54597f651a83f25c6b68e557aba5961` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/summary.v1.json` | `ai/app/research/research2/rules/summary.v1.json` | `9617003dbc3791d3cadf013d4c9adb7e49d18451` | `9617003dbc3791d3cadf013d4c9adb7e49d18451` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/synthesize.v1.json` | `ai/app/research/research2/rules/synthesize.v1.json` | `969036e73e579cdf5c969716eeef5cb0555f30c8` | `969036e73e579cdf5c969716eeef5cb0555f30c8` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/trust_labels.v1.json` | `ai/app/research/research2/rules/trust_labels.v1.json` | `1d5eac934db249a85e831929138fd3b91bef8ad1` | `1d5eac934db249a85e831929138fd3b91bef8ad1` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/units.v1.json` | `ai/app/research/research2/rules/units.v1.json` | `3099fb2df4ff4a1ceb5527a99aeada9ce5db3483` | `3099fb2df4ff4a1ceb5527a99aeada9ce5db3483` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/whitelist.v3.json` | `ai/app/research/research2/rules/whitelist.v3.json` | `e2b79b27a80653ae1554d4fb045675da7bfcd855` | `e2b79b27a80653ae1554d4fb045675da7bfcd855` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/whitelist.v4.json` | `ai/app/research/research2/rules/whitelist.v4.json` | `2c786cc1967b18e81eeac7c047d1eda73dad18e5` | `2c786cc1967b18e81eeac7c047d1eda73dad18e5` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/whitelist.v5.json` | `ai/app/research/research2/rules/whitelist.v5.json` | `ec27683541a7201d02a6e661ff46a0e1c572ec32` | `ec27683541a7201d02a6e661ff46a0e1c572ec32` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/whitelist.v6.json` | `ai/app/research/research2/rules/whitelist.v6.json` | `987a22b482436b7534d6c1e2b02f475f56a36fdf` | `987a22b482436b7534d6c1e2b02f475f56a36fdf` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/whitelist.v7.json` | `ai/app/research/research2/rules/whitelist.v7.json` | `764fd5826b47a5955a40315727cc7e15b51e9b6d` | `764fd5826b47a5955a40315727cc7e15b51e9b6d` | BYTE_IDENTICAL |
| `ai/app/research/research2/rules/whitelist.v8.json` | `ai/app/research/research2/rules/whitelist.v8.json` | `e8763754d0020477f40bee20eb59f5d71971a6b8` | `e8763754d0020477f40bee20eb59f5d71971a6b8` | BYTE_IDENTICAL |
| `ai/app/research/research2/run.py` | `ai/app/research/research2/run.py` | `ce34157da4afda2c828d36c5a86e7ccb13aeb8c1` | `ce34157da4afda2c828d36c5a86e7ccb13aeb8c1` | BYTE_IDENTICAL |
| `ai/app/research/research2/runlog.py` | `ai/app/research/research2/runlog.py` | `b9065ecfdf65dbebb53bc92a917518fffe1833d5` | `b9065ecfdf65dbebb53bc92a917518fffe1833d5` | BYTE_IDENTICAL |
| `ai/app/research/research2/runpath.py` | `ai/app/research/research2/runpath.py` | `31968597ec130638377ad656f11c211150892d17` | `31968597ec130638377ad656f11c211150892d17` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/beauty-13/a3_bodies.json` | `ai/app/research/research2/runs/beauty-13/a3_bodies.json` | `ec1dabe7966687b8b765da5e1f0b85f97c43de00` | `ec1dabe7966687b8b765da5e1f0b85f97c43de00` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/beauty-13/result.json` | `ai/app/research/research2/runs/beauty-13/result.json` | `03fd74c924d40f5536b25b1bdee5bc4bd3f4aa4f` | `03fd74c924d40f5536b25b1bdee5bc4bd3f4aa4f` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/beauty-13/run.jsonl` | `ai/app/research/research2/runs/beauty-13/run.jsonl` | `32059ea79d16b52c09d6cee4beeeeb34c67da1b1` | `32059ea79d16b52c09d6cee4beeeeb34c67da1b1` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/ledger-05/a3_bodies.json` | `ai/app/research/research2/runs/ledger-05/a3_bodies.json` | `b3230993f01b01b73703b107d80f67aa08fdc3fe` | `b3230993f01b01b73703b107d80f67aa08fdc3fe` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/ledger-05/result.json` | `ai/app/research/research2/runs/ledger-05/result.json` | `d8889454e90b99855cb9b367843415df523da512` | `d8889454e90b99855cb9b367843415df523da512` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/ledger-05/run.jsonl` | `ai/app/research/research2/runs/ledger-05/run.jsonl` | `04ba4dc1ce2a49b5f34a2580165b29b93f793139` | `04ba4dc1ce2a49b5f34a2580165b29b93f793139` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/ledger-05/summary.json` | `ai/app/research/research2/runs/ledger-05/summary.json` | `295e104d1c1bf11914bc9a31f27adf3d2d428473` | `295e104d1c1bf11914bc9a31f27adf3d2d428473` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/pet-treat-15/a3_bodies.json` | `ai/app/research/research2/runs/pet-treat-15/a3_bodies.json` | `a3d5af4e9de8f8f35cb0a46330493f1b6f999715` | `a3d5af4e9de8f8f35cb0a46330493f1b6f999715` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/pet-treat-15/golden_probe.json` | `ai/app/research/research2/runs/pet-treat-15/golden_probe.json` | `e3524e5e264a5106c27b2ab5a4ccd19821637b54` | `e3524e5e264a5106c27b2ab5a4ccd19821637b54` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/pet-treat-15/result.json` | `ai/app/research/research2/runs/pet-treat-15/result.json` | `f2ddf5e68a4af2c0485911e3609179adec29e93b` | `f2ddf5e68a4af2c0485911e3609179adec29e93b` | BYTE_IDENTICAL |
| `ai/app/research/research2/runs/pet-treat-15/run.jsonl` | `ai/app/research/research2/runs/pet-treat-15/run.jsonl` | `648104f4668d3b9f54c206e35ae9b654a9a11151` | `648104f4668d3b9f54c206e35ae9b654a9a11151` | BYTE_IDENTICAL |
| `ai/app/research/research2/schema.py` | `ai/app/research/research2/schema.py` | `fec9aefc177b86b5b6f05a17e3105c5f10e6867e` | `fec9aefc177b86b5b6f05a17e3105c5f10e6867e` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/bm_adapter.py` | `ai/app/research/research2/service/bm_adapter.py` | `db3c83f14c3ad2cfe58d263991b41598cb5b2851` | `db3c83f14c3ad2cfe58d263991b41598cb5b2851` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/bm_export.py` | `ai/app/research/research2/service/bm_export.py` | `78a204cf28a35f38242e7850b04b3050f48a4d05` | `78a204cf28a35f38242e7850b04b3050f48a4d05` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/bm_layer.py` | `ai/app/research/research2/service/bm_layer.py` | `305d80bc2b3cf40851e98418c41e45ce1cdb6fab` | `305d80bc2b3cf40851e98418c41e45ce1cdb6fab` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/bm_scorer.py` | `ai/app/research/research2/service/bm_scorer.py` | `7afdf0d9d70c937afc2fbaf651ba0ac88b30ed27` | `7afdf0d9d70c937afc2fbaf651ba0ac88b30ed27` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/canvas.py` | `ai/app/research/research2/service/canvas.py` | `77e1caf70b369ca800c84a62495c09938e3b1be0` | `77e1caf70b369ca800c84a62495c09938e3b1be0` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/cards.py` | `ai/app/research/research2/service/cards.py` | `91924f0040afc532052785102058617de8df54ad` | `91924f0040afc532052785102058617de8df54ad` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/summary.py` | `ai/app/research/research2/service/summary.py` | `e9b44ca85a832600b7611fc7844b5a5a33233d4b` | `e9b44ca85a832600b7611fc7844b5a5a33233d4b` | BYTE_IDENTICAL |
| `ai/app/research/research2/service/verdict.py` | `ai/app/research/research2/service/verdict.py` | `7475e3569d6a2808da6037359af0a3ac87ada60b` | `7475e3569d6a2808da6037359af0a3ac87ada60b` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/calibrate_content.json` | `ai/app/research/research2/tests/calibrate_content.json` | `5430f9b3198e17c7bb186a1b3934459d0428564b` | `5430f9b3198e17c7bb186a1b3934459d0428564b` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/calibrate_content.py` | `ai/app/research/research2/tests/calibrate_content.py` | `9a166922521f08d6d71a1e2b77d23147b135675b` | `9a166922521f08d6d71a1e2b77d23147b135675b` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/cases_numbers.json` | `ai/app/research/research2/tests/cases_numbers.json` | `580e884950c492d1c5b87dab413544f79f656b6d` | `580e884950c492d1c5b87dab413544f79f656b6d` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/probe_notfound.json` | `ai/app/research/research2/tests/probe_notfound.json` | `d656fd3e18caca953190e5fb6711f525f56f9baf` | `d656fd3e18caca953190e5fb6711f525f56f9baf` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/probe_notfound.py` | `ai/app/research/research2/tests/probe_notfound.py` | `cc7c6d998868ab225262add0c750d330d9d09bb1` | `cc7c6d998868ab225262add0c750d330d9d09bb1` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_bm_export.py` | `ai/app/research/research2/tests/test_bm_export.py` | `13d2e7ec4d20e2e5d82ec0f197ba8db21338c0bc` | `13d2e7ec4d20e2e5d82ec0f197ba8db21338c0bc` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_bm_layer.py` | `ai/app/research/research2/tests/test_bm_layer.py` | `48cb1a055f8be41adfd5c8d1ea3db0069cbb5a72` | `48cb1a055f8be41adfd5c8d1ea3db0069cbb5a72` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_bm_scorer.py` | `ai/app/research/research2/tests/test_bm_scorer.py` | `2acd58b53d220e9c0a84e92408ba3abb1d02a19e` | `2acd58b53d220e9c0a84e92408ba3abb1d02a19e` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_design_score.py` | `ai/app/research/research2/tests/test_design_score.py` | `9339072e570701735aca2f3c56722b3031ad6ba1` | `9339072e570701735aca2f3c56722b3031ad6ba1` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_failopen.py` | `ai/app/research/research2/tests/test_failopen.py` | `c39b17a2c50d5a5e2d8d6e5ff21e63485d5a85ae` | `c39b17a2c50d5a5e2d8d6e5ff21e63485d5a85ae` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_harness.py` | `ai/app/research/research2/tests/test_harness.py` | `89d9603c9a8bb1d1b2e27d17f0828d772cbaa675` | `89d9603c9a8bb1d1b2e27d17f0828d772cbaa675` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step1.py` | `ai/app/research/research2/tests/test_step1.py` | `955363b640e1501c1bf14cb0bd58a1dbd40f31fa` | `955363b640e1501c1bf14cb0bd58a1dbd40f31fa` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step10.py` | `ai/app/research/research2/tests/test_step10.py` | `f130f6d52775070b06efe8b732d6a7bd1c820808` | `f130f6d52775070b06efe8b732d6a7bd1c820808` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step11.py` | `ai/app/research/research2/tests/test_step11.py` | `c0cc77cc83bd432d0623025558d9f72c4738af96` | `c0cc77cc83bd432d0623025558d9f72c4738af96` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step12.py` | `ai/app/research/research2/tests/test_step12.py` | `59b5e04c005d820f8756f079e3fdb83d265c2bd9` | `59b5e04c005d820f8756f079e3fdb83d265c2bd9` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step13.py` | `ai/app/research/research2/tests/test_step13.py` | `64e2e0ff0fc570ecd7e570dc52062e644555c26d` | `64e2e0ff0fc570ecd7e570dc52062e644555c26d` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step14.py` | `ai/app/research/research2/tests/test_step14.py` | `833b2b5e5f0883b82aebc767f3fc81c1ee6351e0` | `833b2b5e5f0883b82aebc767f3fc81c1ee6351e0` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step15.py` | `ai/app/research/research2/tests/test_step15.py` | `72f57367ab008bee2a6afba730ec601f6123a6b4` | `72f57367ab008bee2a6afba730ec601f6123a6b4` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step16.py` | `ai/app/research/research2/tests/test_step16.py` | `fc8dce6de84411d84bcac893aa9822443e05974e` | `fc8dce6de84411d84bcac893aa9822443e05974e` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step17.py` | `ai/app/research/research2/tests/test_step17.py` | `fa6b54cef2fa2536d0b860e2d10fa2ca479bbe90` | `fa6b54cef2fa2536d0b860e2d10fa2ca479bbe90` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step18.py` | `ai/app/research/research2/tests/test_step18.py` | `3b314ec2a1f0605c2163cbdebf62c18515e19204` | `3b314ec2a1f0605c2163cbdebf62c18515e19204` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step2.py` | `ai/app/research/research2/tests/test_step2.py` | `a50677a201a29e58468f2f0b630a1a0db7b55b0f` | `a50677a201a29e58468f2f0b630a1a0db7b55b0f` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step3.py` | `ai/app/research/research2/tests/test_step3.py` | `e6f3b1fcbf89bb74d70208b7fd8f424889c2fe62` | `e6f3b1fcbf89bb74d70208b7fd8f424889c2fe62` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step4.py` | `ai/app/research/research2/tests/test_step4.py` | `f25cc5834273c55749498d3917cd2885922bd5be` | `f25cc5834273c55749498d3917cd2885922bd5be` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step5.py` | `ai/app/research/research2/tests/test_step5.py` | `e1aa28d706bb856a317581b8e36018b4692d0c8a` | `e1aa28d706bb856a317581b8e36018b4692d0c8a` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step6.py` | `ai/app/research/research2/tests/test_step6.py` | `eb22f3c5c71902df492ce936055882c4ca6d6c48` | `eb22f3c5c71902df492ce936055882c4ca6d6c48` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step8.py` | `ai/app/research/research2/tests/test_step8.py` | `5bd40138b6d155bb94511bc883f07809f6940bf6` | `5bd40138b6d155bb94511bc883f07809f6940bf6` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_step9.py` | `ai/app/research/research2/tests/test_step9.py` | `983d3c4a6b2e4678daa0ac500b59518da64b9e28` | `983d3c4a6b2e4678daa0ac500b59518da64b9e28` | BYTE_IDENTICAL |
| `ai/app/research/research2/tests/test_verdict_canvas.py` | `ai/app/research/research2/tests/test_verdict_canvas.py` | `0ef3a98a973210ea3d27e76fbbcfef48cbf8bb61` | `0ef3a98a973210ea3d27e76fbbcfef48cbf8bb61` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/bm_rehearsal/nb_cells.py` | `ai/app/research/research2/tools/bm_rehearsal/nb_cells.py` | `0089c54a527861f86eda36f8f590173a5318e061` | `0089c54a527861f86eda36f8f590173a5318e061` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/bm_rehearsal/nb_llm.py` | `ai/app/research/research2/tools/bm_rehearsal/nb_llm.py` | `771ff292a8ae52299b279e9c1d6e581db0f45e41` | `771ff292a8ae52299b279e9c1d6e581db0f45e41` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/bm_rehearsal/rehearse.py` | `ai/app/research/research2/tools/bm_rehearsal/rehearse.py` | `95b701b53991091cca48f555c7fc7b730e1f9f80` | `95b701b53991091cca48f555c7fc7b730e1f9f80` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/bm_rehearsal/run_full.py` | `ai/app/research/research2/tools/bm_rehearsal/run_full.py` | `9b835371d6ec5e5f86a70fb1b04dc3d0739ce18c` | `9b835371d6ec5e5f86a70fb1b04dc3d0739ce18c` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/bm_roundtrip.py` | `ai/app/research/research2/tools/bm_roundtrip.py` | `700634ddd618101f1bae165e051f67f27abe11f8` | `700634ddd618101f1bae165e051f67f27abe11f8` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/boundary_roundtrip.py` | `ai/app/research/research2/tools/boundary_roundtrip.py` | `f74ab7ffeaeb522d886bde6f9f5e49f6f95d50d6` | `f74ab7ffeaeb522d886bde6f9f5e49f6f95d50d6` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/checklist.py` | `ai/app/research/research2/tools/checklist.py` | `8f3525536bbdb9f5351a771d6a6a8d5b44a5636a` | `8f3525536bbdb9f5351a771d6a6a8d5b44a5636a` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/corpus_probe.py` | `ai/app/research/research2/tools/corpus_probe.py` | `93f723b5b701fc692809fc900fb039593e364ccb` | `93f723b5b701fc692809fc900fb039593e364ccb` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/design_score.py` | `ai/app/research/research2/tools/design_score.py` | `9f0f895b24932c69496b84bb1ccb7e03391c00c4` | `9f0f895b24932c69496b84bb1ccb7e03391c00c4` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/eval_search.py` | `ai/app/research/research2/tools/eval_search.py` | `2ffc653bf7620a2da768caadebfac9938919768d` | `2ffc653bf7620a2da768caadebfac9938919768d` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/extract_triage.py` | `ai/app/research/research2/tools/extract_triage.py` | `354dca381268c6c431281223bf7449005dc94835` | `354dca381268c6c431281223bf7449005dc94835` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/fact_list.py` | `ai/app/research/research2/tools/fact_list.py` | `9e5d3011f8ef79dcfbe285ca8e53ff1fd1523828` | `9e5d3011f8ef79dcfbe285ca8e53ff1fd1523828` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/focus_probe.py` | `ai/app/research/research2/tools/focus_probe.py` | `7b7beb3d50f3091f7f321fc451ac2580dd884a85` | `7b7beb3d50f3091f7f321fc451ac2580dd884a85` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/funnel.py` | `ai/app/research/research2/tools/funnel.py` | `6c6b206c6bd8dadbc165d35c273f069ca27721d8` | `6c6b206c6bd8dadbc165d35c273f069ca27721d8` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/garbage_check.py` | `ai/app/research/research2/tools/garbage_check.py` | `6e48007f73365c116e2e05bf18b16e8013abd560` | `6e48007f73365c116e2e05bf18b16e8013abd560` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/golden_probe.py` | `ai/app/research/research2/tools/golden_probe.py` | `efeea168f24eeee5383d3dd29e4de348255f231c` | `efeea168f24eeee5383d3dd29e4de348255f231c` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/grade_audit.py` | `ai/app/research/research2/tools/grade_audit.py` | `f4edd7cbeb5d9d0f8de83acc34915b614420f7f9` | `f4edd7cbeb5d9d0f8de83acc34915b614420f7f9` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/grade_monotone.py` | `ai/app/research/research2/tools/grade_monotone.py` | `42d5d4b1244acf1d4c265a46266def83c15a41da` | `42d5d4b1244acf1d4c265a46266def83c15a41da` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/harness_agreement.py` | `ai/app/research/research2/tools/harness_agreement.py` | `1816552c34e7902b2108c7ccdf9c5ce737cb2ba6` | `1816552c34e7902b2108c7ccdf9c5ce737cb2ba6` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/harness_variance.py` | `ai/app/research/research2/tools/harness_variance.py` | `579d9df58c0af2e8a3bb1b8f94ef286d1b34f200` | `579d9df58c0af2e8a3bb1b8f94ef286d1b34f200` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/headline.py` | `ai/app/research/research2/tools/headline.py` | `08452a26df6bde8742cb0a046d25cf9d03bf0735` | `08452a26df6bde8742cb0a046d25cf9d03bf0735` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/judge_lines.py` | `ai/app/research/research2/tools/judge_lines.py` | `09094ceee73a2beb9789a1be3b53dcf2e07b7190` | `09094ceee73a2beb9789a1be3b53dcf2e07b7190` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/kosis_probe_series.py` | `ai/app/research/research2/tools/kosis_probe_series.py` | `b21f2d3ae128762c1c5266f3d41f96b951a15066` | `b21f2d3ae128762c1c5266f3d41f96b951a15066` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/kosis_probe.py` | `ai/app/research/research2/tools/kosis_probe.py` | `597d7caaec1ff2226293dc8cf6bcbcb94d45c87f` | `597d7caaec1ff2226293dc8cf6bcbcb94d45c87f` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/no_source_no_value.py` | `ai/app/research/research2/tools/no_source_no_value.py` | `4c6832aae68177c8a416bb31bc2d39f304bf71c7` | `4c6832aae68177c8a416bb31bc2d39f304bf71c7` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/pick_lead.py` | `ai/app/research/research2/tools/pick_lead.py` | `f134329765f52c1dd1e07b55cbf0b2b57b19eb9b` | `f134329765f52c1dd1e07b55cbf0b2b57b19eb9b` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/preflight.py` | `ai/app/research/research2/tools/preflight.py` | `15ff78090aadfdca51e6e31db0279a9c08ec855d` | `15ff78090aadfdca51e6e31db0279a9c08ec855d` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/prescribe.py` | `ai/app/research/research2/tools/prescribe.py` | `e4974e6c8758430c5c3300d2a911cfb784163ad8` | `e4974e6c8758430c5c3300d2a911cfb784163ad8` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/promote_cards.py` | `ai/app/research/research2/tools/promote_cards.py` | `223c30e0a20b7ebdb744b1508957812dc8b397fc` | `223c30e0a20b7ebdb744b1508957812dc8b397fc` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/publish_gate.py` | `ai/app/research/research2/tools/publish_gate.py` | `7728b6611e9f9aea2a4052f84a8943eba4c8621f` | `7728b6611e9f9aea2a4052f84a8943eba4c8621f` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/quote_audit.py` | `ai/app/research/research2/tools/quote_audit.py` | `aeb3c2bccbc8b03c825ebf7c6e2afe3f33d7fe75` | `aeb3c2bccbc8b03c825ebf7c6e2afe3f33d7fe75` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/read_passages.py` | `ai/app/research/research2/tools/read_passages.py` | `6df137b1bd8509c01db5a80231e0e3beceb627b3` | `6df137b1bd8509c01db5a80231e0e3beceb627b3` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/read_sections.py` | `ai/app/research/research2/tools/read_sections.py` | `eb0843f5fd9756bb11e4216daf7e8b8cde017caa` | `eb0843f5fd9756bb11e4216daf7e8b8cde017caa` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/reask_ab.py` | `ai/app/research/research2/tools/reask_ab.py` | `ad379a3df6133c447467ce28cfd3b5479342f17e` | `ad379a3df6133c447467ce28cfd3b5479342f17e` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/reask_sections.py` | `ai/app/research/research2/tools/reask_sections.py` | `242f161fd0e2b530d70bff98b9c954fd4e8e22c3` | `242f161fd0e2b530d70bff98b9c954fd4e8e22c3` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/render_final.py` | `ai/app/research/research2/tools/render_final.py` | `fb899dd6469b3ccb18ec32c42316aed641b07bda` | `fb899dd6469b3ccb18ec32c42316aed641b07bda` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/render_report.py` | `ai/app/research/research2/tools/render_report.py` | `648c50805a388a4bab5f494ff1c691013429d880` | `648c50805a388a4bab5f494ff1c691013429d880` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/render_sections.py` | `ai/app/research/research2/tools/render_sections.py` | `524dc8dbeaec63f9f3f6d32aed2a248fd982697d` | `524dc8dbeaec63f9f3f6d32aed2a248fd982697d` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/replicate_concept.py` | `ai/app/research/research2/tools/replicate_concept.py` | `199f0efa45e76f4cfdd88da706ba5681057d2c17` | `199f0efa45e76f4cfdd88da706ba5681057d2c17` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/scorecard.py` | `ai/app/research/research2/tools/scorecard.py` | `b5315662c59d0f9475f466f91870bbccdd8fca8a` | `b5315662c59d0f9475f466f91870bbccdd8fca8a` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/series_matrix.py` | `ai/app/research/research2/tools/series_matrix.py` | `9cb059c215ea1c760bbb7925944d32bb5023617e` | `9cb059c215ea1c760bbb7925944d32bb5023617e` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/sim_url_filter.py` | `ai/app/research/research2/tools/sim_url_filter.py` | `510c1a8876a97d88eb49d38dd0d44a71c1a95751` | `510c1a8876a97d88eb49d38dd0d44a71c1a95751` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/slot_dryrun.py` | `ai/app/research/research2/tools/slot_dryrun.py` | `de27881d1e9a76a6890063b813609074363d8658` | `de27881d1e9a76a6890063b813609074363d8658` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/summary_check.py` | `ai/app/research/research2/tools/summary_check.py` | `889a079e08b9a48c6b9ea1d8a4a3324dca54f606` | `889a079e08b9a48c6b9ea1d8a4a3324dca54f606` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/synthesize.py` | `ai/app/research/research2/tools/synthesize.py` | `64fb41e16ed4f2d4a59d3c179bed9be3bade4d7c` | `64fb41e16ed4f2d4a59d3c179bed9be3bade4d7c` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/underscore_audit.py` | `ai/app/research/research2/tools/underscore_audit.py` | `98900bfc9bcc9edb47aad118627cc3eebdfbb37e` | `98900bfc9bcc9edb47aad118627cc3eebdfbb37e` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/unit_audit.py` | `ai/app/research/research2/tools/unit_audit.py` | `134d91af73918bb52a79859e9defce8b314e53d6` | `134d91af73918bb52a79859e9defce8b314e53d6` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/write_report.py` | `ai/app/research/research2/tools/write_report.py` | `212eafb94a5426f269fe2009ef6fa690f7033f29` | `212eafb94a5426f269fe2009ef6fa690f7033f29` | BYTE_IDENTICAL |
| `ai/app/research/research2/tools/write_sections.py` | `ai/app/research/research2/tools/write_sections.py` | `b9f72a3cfb956a2d12c15fb51c961c02ff24bd51` | `b9f72a3cfb956a2d12c15fb51c961c02ff24bd51` | BYTE_IDENTICAL |
| `ai/app/research/research2/viewer.html` | `ai/app/research/research2/viewer.html` | `9f436bbf5a4efc32b6b107971531aba1613182cf` | `9f436bbf5a4efc32b6b107971531aba1613182cf` | BYTE_IDENTICAL |
| `ai/app/research/runner.py` | `ai/app/research/runner.py` | `f2c4f7624701562af6b278168d5c5cfaa7bb5c04` | `f2c4f7624701562af6b278168d5c5cfaa7bb5c04` | BYTE_IDENTICAL |
| `ai/app/research/serialize.py` | `ai/app/research/serialize.py` | `269615e2c57343ef5ddf04e763cf3927c99f4e8f` | `269615e2c57343ef5ddf04e763cf3927c99f4e8f` | BYTE_IDENTICAL |
| `ai/app/twin/bank.py` | `ai/app/twin/bank.py` | `8bfa1143e4d9f91c343624a230305e204308c572` | `8bfa1143e4d9f91c343624a230305e204308c572` | BYTE_IDENTICAL |
| `ai/app/twin/profile.py` | `ai/app/twin/profile.py` | `52ecc32ea260d3c9f5f9dfff2c7e57273a71cc8d` | `52ecc32ea260d3c9f5f9dfff2c7e57273a71cc8d` | BYTE_IDENTICAL |
| `backend/src/main/java/com/aivle/backend/integration/ai/AiServerProperties.java` | `backend/src/main/java/com/aivle/backend/integration/ai/AiServerProperties.java` | `ebf86fa6644c76f1a31440b548abf93589669d99` | `ebf86fa6644c76f1a31440b548abf93589669d99` | BYTE_IDENTICAL |
| `backend/src/main/java/com/aivle/backend/pipeline/market/MarketInterviewInputFactory.java` | `backend/src/main/java/com/aivle/backend/pipeline/market/MarketInterviewInputFactory.java` | `a6d12e21dcca525ae33d76d402dba090c8140f6d` | `a6d12e21dcca525ae33d76d402dba090c8140f6d` | BYTE_IDENTICAL |
| `backend/src/main/java/com/aivle/backend/pipeline/market/MarketInterviewWorker.java` | `backend/src/main/java/com/aivle/backend/pipeline/market/MarketInterviewWorker.java` | `735af0a42f0b33ada45565e23221f1b38aa8c657` | `735af0a42f0b33ada45565e23221f1b38aa8c657` | BYTE_IDENTICAL |
| `backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchInputFactory.java` | `backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchInputFactory.java` | `ea4aa6eea508219f9bdb42f55b712934fb5fd339` | `ea4aa6eea508219f9bdb42f55b712934fb5fd339` | BYTE_IDENTICAL |
| `backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchWorker.java` | `backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchWorker.java` | `905630e2ccf2151af9263f4899c2de9d75730be8` | `905630e2ccf2151af9263f4899c2de9d75730be8` | BYTE_IDENTICAL |
| `backend/src/main/java/com/aivle/backend/taskrun/contract/MarketInterviewContract.java` | `backend/src/main/java/com/aivle/backend/taskrun/contract/MarketInterviewContract.java` | `2c525b0e32827bea25733f9f00b583e1ad0aaf85` | `2c525b0e32827bea25733f9f00b583e1ad0aaf85` | BYTE_IDENTICAL |
| `backend/src/main/java/com/aivle/backend/taskrun/contract/MarketResearchContract.java` | `backend/src/main/java/com/aivle/backend/taskrun/contract/MarketResearchContract.java` | `c5edcaf0586a632e4064741796809a7d5a788369` | `c5edcaf0586a632e4064741796809a7d5a788369` | BYTE_IDENTICAL |
| `ai/app/providers/__init__.py` | `ai/app/providers/__init__.py` | `33ffeb645847c04ca102b90f0d3f90922f39177c` | `33ffeb645847c04ca102b90f0d3f90922f39177c` | BYTE_IDENTICAL |
| `ai/app/providers/schema_compatibility.py` | `ai/app/providers/schema_compatibility.py` | `581d453e1e9e1583ec602940e64af2fe77144f5e` | `581d453e1e9e1583ec602940e64af2fe77144f5e` | BYTE_IDENTICAL |
| `ai/app/providers/structured.py` | `ai/app/providers/structured.py` | `6ac8d4dd766820d2ea1b0ff1b8f049a10ed17aeb` | `6ac8d4dd766820d2ea1b0ff1b8f049a10ed17aeb` | BYTE_IDENTICAL |
| `ai/app/twin/caveats.py` | `ai/app/twin/caveats.py` | `cc7ba8d08091e87f8544e2ffe61c9ef72f7f58ee` | `cc7ba8d08091e87f8544e2ffe61c9ef72f7f58ee` | BYTE_IDENTICAL |
| `ai/app/twin/runner.py` | `ai/app/twin/runner.py` | `70d75cef0b5db7b1ec5e1f19ed7bab194b2f6a0f` | `70d75cef0b5db7b1ec5e1f19ed7bab194b2f6a0f` | BYTE_IDENTICAL |
| `ai/app/twin/task_type.py` | `ai/app/twin/task_type.py` | `762218091b3b20e733635f0c6eac32ec61765db9` | `762218091b3b20e733635f0c6eac32ec61765db9` | BYTE_IDENTICAL |

## WRAPPER_ONLY allowlist

| MAIN path/blob | FULL current path/blob | reason | classification |
|---|---|---|---|
| `014e8f4dc00c95a267a9a6c0cbe23a1bb8c55677` | `ai/app/api/executions.py` / `053fa2b860c5203493cfc799065cedb659b764e2` | FULL task registry retained; MARKET_INTERVIEW branch delegates to MAIN | WRAPPER_ONLY |
| `b135f1bf76351bc58d35095473ff26be24b7a8c4` | `backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchController.java` / `524bb81752788ad8cafc037ffd94620f5534b417` | FULL v3 route plus session observation | WRAPPER_ONLY |
| `53c969a2b1c858d7a35653029d1b14cee81531d2` | `backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchService.java` / `6bda929a9914baf714aae6bd48fd069a7fb949aa` | TaskRun/lineage integration and projection read API | WRAPPER_ONLY |
| `b12040635fa6a864bc1169b7731e1697996c3c12` | `backend/src/main/resources/application.yaml` / `b12040635fa6a864bc1169b7731e1697996c3c12` | FULL configuration with MAIN 63m market timeout | WRAPPER_ONLY |
| `cda7bec4a80b4258f2b746b661ec40dd538959aa` | `backend/src/main/java/com/aivle/backend/pipeline/refinement/ConceptRefinementService.java` / `97aace7b510c17fdd96e33c85e1803d21b58eba3` | FULL v3 command; MAIN worker method name delegates at boundary | WRAPPER_ONLY |
| `ABSENT` | `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewController.java` / `f337d70388b48d43c4fc1d7833bdae57003f7c7b` | FULL v3 facade | WRAPPER_ONLY |
| `ABSENT` | `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewInputFactory.java` / `a21cad2e4a88886057943af7b00196c101e817a5` | finalized board/price validation then MAIN input factory delegation | WRAPPER_ONLY |
| `ABSENT` | `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewService.java` / `b14b96c78f6c26dd59ff2126b8c21d3f75b0a5b1` | finalized-source gate, TaskRun lineage and result projection | WRAPPER_ONLY |
| `ABSENT` | `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewRun.java` / `8f4d1e50068a020bfba9d6e548b30ecd104486fd` | lineage storage outside MAIN result | WRAPPER_ONLY |
| `ABSENT` | `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewSourceResolver.java` / `bad19ecee4baf8f2d91ccc397cc927579cfb3cf5` | Stage4 finalized-source gate | WRAPPER_ONLY |

Allowed differences are limited to authentication/current user, FULL v3 route prefix, TaskRun common infrastructure, ConceptRefinementFinal gate, lineage DB columns, job-event transport, safe logging/correlation id, and current endpoint projection. They cannot alter budget, timeout, collection/search, section recall, evidence, BM routing, targeting, sampling, questions, coding, verification, failure semantics or result semantics.

## NOT_ALLOWED as production authority

| path | current classification |
|---|---|
| `ai/app/tasks/market_interview/**` | NOT_ALLOWED; files remain historical/dormant and no production dispatch imports them |
| `ai/app/research/research2/section_recall.py` | NOT_ALLOWED; removed |
| `ai/app/research/research2/rules/section_recall.v1.json` | NOT_ALLOWED; removed |
| `ai/app/research/semantic_relevance.py` | NOT_ALLOWED; removed |
| `backend/.../pipeline/marketinterview/MarketInterviewWorker.java` | NOT_ALLOWED; removed so only MAIN worker claims MARKET_INTERVIEW |
| `MarketStrategySelector` in Stage 2/4 input construction | NOT_ALLOWED; neither active input factory reaches it |

## Verification command

`ai/tests/test_main_frozen_core_equivalence.py` canonicalizes CRLF through Git clean semantics, compares every listed donor blob to `origin/main`, asserts the exact dispatch import, rejects the two Stage 2 replacement files, and proves the FULL Stage 4 worker authority is absent.
