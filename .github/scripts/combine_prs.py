#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
combine_prs.py

Script de consolidation automatique des Pull Requests ouvertes vers une branche unique (Rollup PR).
Fonctionne aussi bien en local qu'au sein d'un workflow GitHub Actions.
"""

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

# Support UTF-8 sur consoles Windows et environnements variés
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")



def run_cmd(cmd: List[str], check: bool = True, capture_output: bool = True) -> subprocess.CompletedProcess:
    """Exécute une commande shell sécurisée."""
    try:
        res = subprocess.run(
            cmd,
            check=check,
            capture_output=capture_output,
            text=True,
            encoding="utf-8",
            errors="replace"
        )
        return res
    except subprocess.CalledProcessError as e:
        if check:
            print(f"❌ Erreur lors de l'exécution de: {' '.join(cmd)}", file=sys.stderr)
            if e.stdout:
                print(f"Stdout: {e.stdout}", file=sys.stderr)
            if e.stderr:
                print(f"Stderr: {e.stderr}", file=sys.stderr)
            raise
        return e


def get_open_prs(label: Optional[str] = None, ignore_drafts: bool = True) -> List[Dict[str, Any]]:
    """Récupère la liste des Pull Requests ouvertes via GitHub CLI."""
    cmd = [
        "gh", "pr", "list",
        "--state", "open",
        "--json", "number,title,headRefName,url,author,isDraft,labels,baseRefName"
    ]
    if label:
        cmd.extend(["--label", label])

    res = run_cmd(cmd, check=True)
    if not res.stdout.strip():
        return []

    try:
        prs = json.loads(res.stdout)
    except json.JSONDecodeError as e:
        print(f"Erreur parsing JSON de gh pr list: {e}", file=sys.stderr)
        return []

    filtered = []
    for pr in prs:
        if ignore_drafts and pr.get("isDraft", False):
            continue
        filtered.append(pr)

    return filtered


def get_existing_rollup_pr(combine_branch: str, base_branch: str) -> Optional[int]:
    """Vérifie si une PR existe déjà pour la branche combinée."""
    cmd = [
        "gh", "pr", "list",
        "--state", "open",
        "--head", combine_branch,
        "--base", base_branch,
        "--json", "number"
    ]
    res = run_cmd(cmd, check=False)
    if res.returncode == 0 and res.stdout.strip():
        try:
            prs = json.loads(res.stdout)
            if prs and len(prs) > 0:
                return prs[0]["number"]
        except Exception:
            pass
    return None


def generate_markdown_summary(
    base_branch: str,
    combine_branch: str,
    merged_prs: List[Dict[str, Any]],
    conflicted_prs: List[Tuple[Dict[str, Any], str]],
    skipped_prs: List[Dict[str, Any]]
) -> str:
    """Génère le corps de la PR et le résumé GitHub Actions."""
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S UTC")
    lines = []
    lines.append(f"# 🚀 Pull Request Consolidée ({len(merged_prs)} PRs fusionnées)")
    lines.append("")
    lines.append(f"> Cette PR regroupe automatiquement l'ensemble des Pull Requests prêtes à être fusionnées dans `{base_branch}`.")
    lines.append(f"> **Branche source** : `{combine_branch}` | **Branche cible** : `{base_branch}` | **Généré le** : `{now_str}`")
    lines.append("")

    if merged_prs:
        lines.append("## ✅ Pull Requests incluses")
        lines.append("")
        lines.append("| PR | Titre | Auteur | Branche |")
        lines.append("| :--- | :--- | :--- | :--- |")
        for pr in merged_prs:
            author_login = pr.get("author", {}).get("login", "inconnu") if isinstance(pr.get("author"), dict) else str(pr.get("author", "inconnu"))
            lines.append(f"| [#{pr['number']}]({pr['url']}) | {pr['title']} | @{author_login} | `{pr['headRefName']}` |")
        lines.append("")

    if conflicted_prs:
        lines.append("## ⚠️ PRs en conflit (non incluses)")
        lines.append("")
        lines.append("| PR | Titre | Auteur | Raison du conflit |")
        lines.append("| :--- | :--- | :--- | :--- |")
        for pr, err in conflicted_prs:
            author_login = pr.get("author", {}).get("login", "inconnu") if isinstance(pr.get("author"), dict) else str(pr.get("author", "inconnu"))
            clean_err = err.strip().replace("\n", " ")[:120]
            lines.append(f"| [#{pr['number']}]({pr['url']}) | {pr['title']} | @{author_login} | `{clean_err}` |")
        lines.append("")

    if skipped_prs:
        lines.append("## ℹ️ PRs ignorées")
        lines.append("")
        for pr in skipped_prs:
            lines.append(f"- [#{pr['number']}]({pr['url']}) : {pr['title']} *(Brouillon / Draft)*")
        lines.append("")

    lines.append("---")
    lines.append("💡 *Pour valider ces modifications, fusionnez cette Pull Request dans `" + base_branch + "`.*")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Fusionne toutes les PRs ouvertes en une seule branche / PR consolidée.")
    parser.add_argument("--base-branch", default="main", help="Branche cible principale (défaut: main)")
    parser.add_argument("--combine-branch", default="rollup/all-prs", help="Nom de la branche consolidée (défaut: rollup/all-prs)")
    parser.add_argument("--label", default=None, help="Filtrer par label spécifique")
    parser.add_argument("--include-drafts", action="store_true", help="Inclure également les PRs en mode brouillon")
    parser.add_argument("--dry-run", action="store_true", help="Simuler les fusions sans pousser ni créer de PR")
    parser.add_argument("--no-pr", action="store_true", help="Ne pas créer/mettre à jour la Pull Request sur GitHub")
    args = parser.parse_args()

    print("==================================================")
    print(" 🔄 Consolidation des Pull Requests (Rollup PR)")
    print(f" Base branch    : {args.base_branch}")
    print(f" Combine branch : {args.combine_branch}")
    print(f" Label filter   : {args.label or 'Aucun (toutes les PRs ouvertes)'}")
    print(f" Mode           : {'Simulation (DRY-RUN)' if args.dry_run else 'Exécution'}")
    print("==================================================\n")

    # 1. Configuration Git d'identité si non définie
    run_cmd(["git", "config", "--global", "user.name", "github-actions[bot]"], check=False)
    run_cmd(["git", "config", "--global", "user.email", "github-actions[bot]@users.noreply.github.com"], check=False)

    # 2. Récupérer les PRs ouvertes
    print("🔍 Récupération des Pull Requests ouvertes via GitHub CLI...")
    all_prs = get_open_prs(label=args.label, ignore_drafts=not args.include_drafts)

    # Filtrer la PR de rollup elle-même pour éviter les boucles
    candidate_prs = [
        pr for pr in all_prs 
        if pr.get("headRefName") != args.combine_branch and pr.get("baseRefName") == args.base_branch
    ]

    print(f"👉 {len(candidate_prs)} PR(s) candidate(s) trouvée(s).")
    if not candidate_prs:
        print("✅ Aucune Pull Request ouverte à consolider.")
        # Écrire dans le step summary si présent
        summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_file:
            with open(summary_file, "a", encoding="utf-8") as f:
                f.write("### ℹ️ Consolidation des PRs\nAucune Pull Request ouverte à fusionner.\n")
        return

    # 3. Préparer la branche locale
    print(f"📥 Mise à jour de la branche de base origin/{args.base_branch}...")
    run_cmd(["git", "fetch", "origin", args.base_branch], check=True)

    print(f"🌿 Création/Réinitialisation de la branche locale '{args.combine_branch}' depuis 'origin/{args.base_branch}'...")
    run_cmd(["git", "checkout", "-B", args.combine_branch, f"origin/{args.base_branch}"], check=True)

    merged_prs = []
    conflicted_prs = []
    skipped_prs = []

    # 4. Fusionner chaque PR successivement
    for pr in candidate_prs:
        pr_num = pr["number"]
        pr_title = pr["title"]
        pr_branch = pr["headRefName"]

        print(f"\n🔄 Traitement PR #{pr_num}: '{pr_title}' (branche: {pr_branch})...")

        # Fetcher la PR
        fetch_res = run_cmd(["git", "fetch", "origin", f"pull/{pr_num}/head"], check=False)
        if fetch_res.returncode != 0:
            print(f"⚠️ Impossible de fetcher origin pull/{pr_num}/head. Tentative par nom de branche...")
            fetch_res = run_cmd(["git", "fetch", "origin", pr_branch], check=False)
            if fetch_res.returncode != 0:
                print(f"❌ Échec du fetch pour la PR #{pr_num}.")
                conflicted_prs.append((pr, "Échec du git fetch"))
                continue

        # Tentative de fusion
        merge_msg = f"Merge PR #{pr_num}: {pr_title}"
        merge_res = run_cmd(["git", "merge", "--no-ff", "-m", merge_msg, "FETCH_HEAD"], check=False)

        if merge_res.returncode == 0:
            print(f"✅ PR #{pr_num} fusionnée avec succès !")
            merged_prs.append(pr)
        else:
            print(f"⚠️ Conflit de fusion détecté pour la PR #{pr_num} ! Annulation...")
            run_cmd(["git", "merge", "--abort"], check=False)
            conflicted_prs.append((pr, merge_res.stderr or merge_res.stdout or "Conflits de fusion"))

    print("\n==================================================")
    print(f"📊 Bilan des fusions :")
    print(f" - {len(merged_prs)} PR(s) fusionnée(s) avec succès")
    print(f" - {len(conflicted_prs)} PR(s) en conflit / ignorée(s)")
    print("==================================================")

    if not merged_prs:
        print("⚠️ Aucune PR n'a pu être fusionnée avec succès.")
        return

    # Générer le rapport markdown
    markdown_body = generate_markdown_summary(
        base_branch=args.base_branch,
        combine_branch=args.combine_branch,
        merged_prs=merged_prs,
        conflicted_prs=conflicted_prs,
        skipped_prs=skipped_prs
    )

    # 5. Step summary GitHub Actions
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        try:
            with open(summary_file, "a", encoding="utf-8") as f:
                f.write(markdown_body + "\n")
        except Exception as e:
            print(f"Avertissement lors de l'écriture du Step Summary: {e}")

    if args.dry_run:
        print("\n[DRY RUN] Résumé généré :\n")
        print(markdown_body)
        return

    # 6. Pousser la branche combinée
    print(f"\n🚀 Envoi de la branche consolidée sur 'origin/{args.combine_branch}'...")
    push_res = run_cmd(["git", "push", "-f", "origin", args.combine_branch], check=False)
    if push_res.returncode != 0:
        print(f"❌ Échec de l'envoi de la branche consolidée vers origin: {push_res.stderr}", file=sys.stderr)
        sys.exit(1)

    # 7. Créer ou mettre à jour la Pull Request
    if not args.no_pr:
        existing_pr_number = get_existing_rollup_pr(args.combine_branch, args.base_branch)
        pr_title = f"chore(rollup): consolidation de {len(merged_prs)} Pull Requests dans {args.base_branch}"

        if existing_pr_number:
            print(f"📝 Mise à jour de la PR existante #{existing_pr_number}...")
            run_cmd([
                "gh", "pr", "edit", str(existing_pr_number),
                "--title", pr_title,
                "--body", markdown_body
            ], check=True)
            print(f"🎉 Pull Request consolidée #{existing_pr_number} mise à jour avec succès !")
        else:
            print(f"✨ Création d'une nouvelle Pull Request consolidée...")
            create_res = run_cmd([
                "gh", "pr", "create",
                "--base", args.base_branch,
                "--head", args.combine_branch,
                "--title", pr_title,
                "--body", markdown_body
            ], check=True)
            print(f"🎉 Pull Request créée : {create_res.stdout.strip()}")


if __name__ == "__main__":
    main()
